#!/usr/bin/env bash
# Provisiona la mensajería de Documentación y Seguros (HU-DOC-10, DAS sec. 8.2). Es idempotente: se puede ejecutar
# varias veces y deja el mismo resultado.
#
# Crea:
#   - Tópico propio fleetops_document_events (lo usará el publicador del outbox).
#   - Por cada productor (Conductores y Vehículos): una cola propia, su DLQ con redrive policy de 3 recepciones,
#     la política de la cola que solo deja escribir al tópico del productor y la suscripción con raw message
#     delivery y FilterPolicy sobre el Message Attribute event_type.
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

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

create_topic() {
	aws sns create-topic --name "$1" --query TopicArn --output text
}

queue_arn() {
	aws sqs get-queue-attributes --queue-url "$1" --attribute-names QueueArn --query Attributes.QueueArn --output text
}

# $1 cola, $2 ARN de la DLQ
create_queue() {
	local attributes="$WORK_DIR/queue-$1.json"
	local redrive
	redrive="{\\\"deadLetterTargetArn\\\":\\\"$2\\\",\\\"maxReceiveCount\\\":\\\"$MAX_RECEIVE_COUNT\\\"}"
	cat >"$attributes" <<JSON
{
  "VisibilityTimeout": "$SQS_VISIBILITY_TIMEOUT_SECONDS",
  "ReceiveMessageWaitTimeSeconds": "$RECEIVE_WAIT_SECONDS",
  "SqsManagedSseEnabled": "true",
  "RedrivePolicy": "$redrive"
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
	local policy="$WORK_DIR/policy-$queue.json"
	local statement
	statement="{\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[{\\\"Sid\\\":\\\"AllowProducerTopic\\\","
	statement+="\\\"Effect\\\":\\\"Allow\\\",\\\"Principal\\\":{\\\"Service\\\":\\\"sns.amazonaws.com\\\"},"
	statement+="\\\"Action\\\":\\\"sqs:SendMessage\\\",\\\"Resource\\\":\\\"$queue\\\","
	statement+="\\\"Condition\\\":{\\\"ArnEquals\\\":{\\\"aws:SourceArn\\\":\\\"$topic\\\"}}}]}"
	printf '{"Policy": "%s"}\n' "$statement" >"$policy"
	aws sqs set-queue-attributes --queue-url "$queue_url" --attributes "file://$policy"
}

# $1 ARN del tópico, $2 ARN de la cola, $3.. event_type aceptados
subscribe() {
	local topic="$1" queue="$2"
	shift 2
	local types=""
	for type in "$@"; do
		types+="${types:+,}\\\"$type\\\""
	done
	local attributes="$WORK_DIR/subscription-${queue##*:}.json"
	cat >"$attributes" <<JSON
{
  "RawMessageDelivery": "true",
  "FilterPolicy": "{\\"event_type\\":[$types]}"
}
JSON
	aws sns subscribe --topic-arn "$topic" --protocol sqs --notification-endpoint "$queue" \
		--attributes "file://$attributes" --return-subscription-arn --query SubscriptionArn --output text
}

# $1 ARN o nombre del tópico del productor, $2 cola, $3 DLQ, $4.. event_type
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
	echo "  $queue <- $topic [$*]"
	echo "    DLQ: $dlq (maxReceiveCount=$MAX_RECEIVE_COUNT) | suscripción: $subscription"
}

echo "Provisionando mensajería de Documentación en $AWS_REGION ${AWS_ENDPOINT_URL:+(emulador $AWS_ENDPOINT_URL)}"
echo "  Tópico propio: $(create_topic fleetops_document_events)"

# Conductores: driver_activated_event confirmado (08-11-2026); driver_deactivated_event SUPUESTO
provision_consumer "${DRIVER_TOPIC_ARN:-fleetops_driver_events}" \
	fleetops_document_driver_events_queue fleetops_document_driver_events_dlq \
	driver_activated_event driver_deactivated_event

# Vehículos: tópico y eventos SUPUESTOS, por confirmar con ese equipo (DAS sec. 8.2)
provision_consumer "${VEHICLE_TOPIC_ARN:-fleetops_vehicle_events}" \
	fleetops_document_vehicle_events_queue fleetops_document_vehicle_events_dlq \
	vehicle_created_event vehicle_decommissioned_event

echo "Listo."
