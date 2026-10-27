/**
 * Adaptador de entrada de mensajería: consumidor de réplicas (HU-DOC-10, DAS sec. 8.2).
 *
 * <p><b>Estado:</b> vacío a propósito. El broker es AWS SNS + SQS (ADR-DOC-12, ADR-DOC-14), pero los
 * tópicos y colas todavía no están provisionados.
 *
 * <p><b>Contenido previsto:</b> dos listeners SQS idempotentes, uno por cola propia:
 * {@code fleetops_documents_vehicle_events_queue} (suscrita a {@code fleetops_vehicles_events}, Message
 * Attribute {@code event_type} en {@code vehicle_created_event}, {@code vehicle_decommissioned_event}) y
 * {@code fleetops_documents_driver_events_queue} (suscrita a {@code fleetops_drivers_events}, con
 * {@code driver_created_event} y {@code driver_deactivated_event}). Los nombres de esos dos tópicos de
 * origen son un SUPUESTO: Vehículos y Conductores deben confirmarlos (DAS sec. 8.2). Ambos listeners
 * invocan {@code SyncOwnerReplicaUseCase}. Deben borrar el mensaje solo tras procesar con éxito, descartar
 * eventos más antiguos ({@code occurred_at} frente a {@code last_event_at}) y usar la redrive policy de la
 * cola (maxReceiveCount = 3) hacia su DLQ ({@code ..._dlq}).
 *
 * <p><b>Patrones:</b> Idempotent Consumer, Retry (visibility timeout de SQS) y Dead Letter Channel.
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging;
