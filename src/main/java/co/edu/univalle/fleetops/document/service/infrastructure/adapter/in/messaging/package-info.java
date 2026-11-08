/**
 * Adaptador de entrada de mensajería: consumidor de réplicas (HU-DOC-10, DAS sec. 8.2).
 *
 * <p><b>Estado:</b> vacío a propósito. El broker es AWS SNS + SQS (ADR-DOC-12, ADR-DOC-14), pero los
 * tópicos y colas todavía no están provisionados.
 *
 * <p><b>Contenido previsto:</b> dos listeners SQS idempotentes, uno por cola propia:
 * {@code fleetops_document_driver_events_queue}, suscrita a {@code fleetops_driver_events} con el Message
 * Attribute {@code event_type} en {@code driver_activated_event} (confirmado por Conductores el 08-11-2026) y
 * {@code driver_deactivated_event} (SUPUESTO); y {@code fleetops_document_vehicle_events_queue}, suscrita a
 * {@code fleetops_vehicle_events} con {@code vehicle_created_event} y {@code vehicle_decommissioned_event}
 * (los tres, SUPUESTOS por confirmar con Vehículos; DAS sec. 8.2). Ambos listeners invocan
 * {@code SyncOwnerReplicaUseCase}. Deben borrar el mensaje solo tras procesar con éxito, descartar eventos
 * más antiguos ({@code occurred_at}, comparado como instante sin importar la zona horaria con que llegue, frente a
 * {@code last_event_at}) y usar la redrive policy de la cola (maxReceiveCount = 3) hacia su DLQ
 * ({@code ..._dlq}). Criterios completos: HU-DOC-10 en {@code docs/G01_Historias_Usuario.xlsx}.
 *
 * <p><b>Patrones:</b> Idempotent Consumer, Retry (visibility timeout de SQS) y Dead Letter Channel.
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging;
