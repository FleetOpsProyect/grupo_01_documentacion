#!/usr/bin/env bash
# Publica un evento de ejemplo como lo haría su productor: el sobre JSON en el cuerpo y el Message Attribute
# event_type, por el que filtran las suscripciones (HU-DOC-10, ADR-DOC-14).
#
# El valor del Message Attribute se pasa aparte porque puede diferir del event_type del cuerpo: el alta de
# vehículos lleva VEHICLE_CREATED en el cuerpo y vehicle_created_event en el atributo.
#
# Uso: publish.sh <nombre del tópico> <valor de event_type> <archivo JSON>
#   publish.sh fleetops_vehicle_events vehicle_created_event /events/vehicle_created_event.json
set -euo pipefail

if [[ $# -ne 3 ]]; then
	echo "Uso: publish.sh <nombre del tópico> <valor de event_type> <archivo JSON>" >&2
	exit 2
fi

topic_name="$1"
event_type="$2"
event_file="$3"

if [[ ! "$event_type" =~ ^[a-z_]+$ ]]; then
	echo "event_type debe ir en snake_case, por ejemplo vehicle_created_event" >&2
	exit 2
fi

topic_arn="$(aws sns create-topic --name "$topic_name" --query TopicArn --output text)"
aws sns publish --topic-arn "$topic_arn" --message "file://$event_file" \
	--message-attributes "{\"event_type\":{\"DataType\":\"String\",\"StringValue\":\"$event_type\"}}" \
	--query MessageId --output text
echo "Publicado $event_type en $topic_name"
