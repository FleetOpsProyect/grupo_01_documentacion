#!/usr/bin/env bash
# Provisiona la mensajería de Documentación y Seguros (HU-DOC-10, DAS sec. 8.2). Es idempotente: se puede ejecutar
# varias veces y deja el mismo resultado; también corrige recursos que ya existían con otros atributos.
#
# Crea:
#   - Tópico propio fleetops_document_events (lo usará el publicador del outbox).
#   - Por cada productor (Conductores y Vehículos): una cola propia, su DLQ con redrive policy de 3 recepciones,
#     la política de la cola que solo deja escribir al tópico del productor y la suscripción con raw message
#     delivery y FilterPolicy sobre el Message Attribute event_type (ADR-DOC-14). Nunca se filtra por el cuerpo.
#
# Variables:
#   AWS_REGION                       región (obligatoria)
#   SQS_VISIBILITY_TIMEOUT_SECONDS   visibility timeout de las colas (obligatoria)
#   AWS_ENDPOINT_URL                 solo con el emulador; vacía contra AWS real
#   DRIVER_TOPIC_ARN, VEHICLE_TOPIC_ARN
#                                    ARN de los tópicos de los productores. Si están vacías, el script crea el tópico
#                                    por nombre en esta cuenta (emulador). En AWS real, use los ARN que entreguen
#                                    Conductores y Vehículos; si están en otra cuenta, ellos deben permitir la
#                                    suscripción en la política de su tópico.
set -euo pipefail

: "${AWS_REGION:?Defina AWS_REGION}"
: "${SQS_VISIBILITY_TIMEOUT_SECONDS:?Defina SQS_VISIBILITY_TIMEOUT_SECONDS}"

readonly MAX_RECEIVE_COUNT=3
# 14 días: el máximo de SQS, para tener tiempo de revisar los mensajes que fallan
readonly DLQ_RETENTION_SECONDS=1209600
# Long polling: menos lecturas vacías y menor costo (recomendación de AWS)
readonly RECEIVE_WAIT_SECONDS=20
# El filtro se aplica sobre el Message Attribute event_type, no sobre el cuerpo (ADR-DOC-14)
readonly FILTER_POLICY_SCOPE=MessageAttributes

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

create_topic() {
	aws sns create-topic --name "$1" --query TopicArn --output text
}

queue_arn() {
	aws sqs get-queue-attributes --queue-url "$1" --attribute-names QueueArn --query Attributes.QueueArn --output text
}

# Escapa las comillas de un JSON para guardarlo como valor de texto dentro de otro JSON
escape_json() {
	printf '%s' "${1//\"/\\\"}"
}

# $1 cola, $2 ARN de la DLQ
create_queue() {
	local attributes="$WORK_DIR/queue-$1.json"
	local redrive
	redrive="{\"deadLetterTargetArn\":\"$2\",\"maxReceiveCount\":\"$MAX_RECEIVE_COUNT\"}"
	cat >"$attributes" <<JSON
{
  "VisibilityTimeout": "$SQS_VISIBILITY_TIMEOUT_SECONDS",
  "ReceiveMessageWaitTimeSeconds": "$RECEIVE_WAIT_SECONDS",
  "SqsManagedSseEnabled": "true",
  "RedrivePolicy": "$(escape_json "$redrive")"
}
JSON
	local url
	url="$(aws sqs create-queue --queue-name "$1" --query QueueUrl --output text)"
	# set-queue-attributes también corrige una cola que ya existía con otros valores
	aws sqs set-queue-attributes --queue-url "$url" --attributes "file://$attributes"
	echo "$url"
}

create_dlq() {
	local url
	url="$(aws sqs create-queue --queue-name "$1" --query QueueUrl --output text)"
	aws sqs set-queue-attributes --queue-url "$url" \
		--attributes "MessageRetentionPeriod=$DLQ_RETENTION_SECONDS,SqsManagedSseEnabled=true"
	echo "$url"
}

# Solo el tópico del productor puede escribir en la cola (aws:SourceArn evita el «confused deputy»)
allow_topic() {
	local queue_url="$1" queue="$2" topic="$3"
	local policy="$WORK_DIR/policy-${queue##*:}.json"
	local statement
	statement="{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"AllowProducerTopic\","
	statement+="\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"sns.amazonaws.com\"},"
	statement+="\"Action\":\"sqs:SendMessage\",\"Resource\":\"$queue\","
	statement+="\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"$topic\"}}}]}"
	printf '{"Policy": "%s"}\n' "$(escape_json "$statement")" >"$policy"
	aws sqs set-queue-attributes --queue-url "$queue_url" --attributes "file://$policy"
}

# $1 ARN del tópico, $2 ARN de la cola, $3.. valores aceptados del Message Attribute event_type.
# Si la suscripción ya existe, actualiza sus atributos: SNS rechaza un «subscribe» repetido con atributos distintos
subscribe() {
	local topic="$1" queue="$2"
	shift 2
	local types=""
	for type in "$@"; do
		types+="${types:+,}\"$type\""
	done
	local filter="{\"event_type\":[$types]}"
	local existing
	existing="$(aws sns list-subscriptions-by-topic --topic-arn "$topic" \
		--query "Subscriptions[?Endpoint=='$queue'].SubscriptionArn | [0]" --output text)"
	if [[ -n "$existing" && "$existing" != "None" && "$existing" != "PendingConfirmation" ]]; then
		aws sns set-subscription-attributes --subscription-arn "$existing" \
			--attribute-name RawMessageDelivery --attribute-value true
		# Primero el alcance y después la política: la política se evalúa con el alcance vigente al definirla
		aws sns set-subscription-attributes --subscription-arn "$existing" \
			--attribute-name FilterPolicyScope --attribute-value "$FILTER_POLICY_SCOPE"
		aws sns set-subscription-attributes --subscription-arn "$existing" \
			--attribute-name FilterPolicy --attribute-value "$filter"
		echo "$existing"
		return
	fi
	local attributes="$WORK_DIR/subscription-${queue##*:}.json"
	cat >"$attributes" <<JSON
{
  "RawMessageDelivery": "true",
  "FilterPolicyScope": "$FILTER_POLICY_SCOPE",
  "FilterPolicy": "$(escape_json "$filter")"
}
JSON
	aws sns subscribe --topic-arn "$topic" --protocol sqs --notification-endpoint "$queue" \
		--attributes "file://$attributes" --return-subscription-arn --query SubscriptionArn --output text
}

# $1 ARN o nombre del tópico del productor, $2 cola, $3 DLQ, $4.. valores del Message Attribute event_type
provision_consumer() {
	local topic="$1" queue="$2" dlq="$3"
	shift 3
	if [[ "$topic" != arn:* ]]; then
		topic="$(create_topic "$topic")"
	fi
	local dlq_url queue_url queue_arn_value dlq_arn_value
	dlq_url="$(create_dlq "$dlq")"
	dlq_arn_value="$(queue_arn "$dlq_url")"
	queue_url="$(create_queue "$queue" "$dlq_arn_value")"
	queue_arn_value="$(queue_arn "$queue_url")"
	allow_topic "$queue_url" "$queue_arn_value" "$topic"
	local subscription
	subscription="$(subscribe "$topic" "$queue_arn_value" "$@")"
	echo "  $queue <- $topic [event_type: $*]"
	echo "    DLQ: $dlq (maxReceiveCount=$MAX_RECEIVE_COUNT) | suscripción: $subscription"
}

echo "Provisionando mensajería de Documentación en $AWS_REGION ${AWS_ENDPOINT_URL:+(emulador $AWS_ENDPOINT_URL)}"
echo "  Tópico propio: $(create_topic fleetops_document_events)"

# Conductores: driver_activated_event confirmado (08-11-2026); driver_deactivated_event SUPUESTO (pendiente P-04)
provision_consumer "${DRIVER_TOPIC_ARN:-fleetops_driver_events}" \
	fleetops_document_driver_events_queue fleetops_document_driver_events_dlq \
	driver_activated_event driver_deactivated_event

# Vehículos: vehicle_created_event y vehicle_deleted_event, confirmados el 15-11-2026
provision_consumer "${VEHICLE_TOPIC_ARN:-fleetops_vehicle_events}" \
	fleetops_document_vehicle_events_queue fleetops_document_vehicle_events_dlq \
	vehicle_created_event vehicle_deleted_event

echo "Listo."
