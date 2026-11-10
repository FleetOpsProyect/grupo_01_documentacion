#!/usr/bin/env bash
# Publica un evento de ejemplo como lo haría su productor: sobre JSON en el cuerpo y el Message Attribute
# event_type, que usa la FilterPolicy de la suscripción (HU-DOC-10).
#
# Uso: publish.sh <nombre del tópico> <archivo JSON>
#   publish.sh fleetops_driver_events /events/driver_activated_event.json
set -euo pipefail

if [[ $# -ne 2 ]]; then
	echo "Uso: publish.sh <nombre del tópico> <archivo JSON>" >&2
	exit 2
fi

topic_name="$1"
event_file="$2"

event_type="$(sed -n 's/.*"event_type"[[:space:]]*:[[:space:]]*"\([a-z_]*\)".*/\1/p' "$event_file" | head -n 1)"
if [[ -z "$event_type" ]]; then
	echo "El archivo $event_file no tiene event_type" >&2
	exit 2
fi

topic_arn="$(aws sns create-topic --name "$topic_name" --query TopicArn --output text)"
aws sns publish --topic-arn "$topic_arn" --message "file://$event_file" \
	--message-attributes "{\"event_type\":{\"DataType\":\"String\",\"StringValue\":\"$event_type\"}}" \
	--query MessageId --output text
echo "Publicado $event_type en $topic_name"
