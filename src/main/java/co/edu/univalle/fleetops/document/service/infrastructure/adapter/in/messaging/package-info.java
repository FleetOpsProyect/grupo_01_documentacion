/**
 * Adaptador de entrada de mensajería: consumidor de réplicas (HU-DOC-10, DAS sec. 8.2).
 *
 * <p><b>Estado:</b> vacío a propósito. El broker (Apache Kafka o AWS SNS + SQS) está por definir
 * (ADR-DOC-12).
 *
 * <p><b>Contenido previsto:</b> consumidor idempotente de {@code vehicle.created},
 * {@code vehicle.decommissioned}, {@code driver.created} y {@code driver.deactivated}, que invoca
 * {@code SyncOwnerReplicaUseCase}. Debe confirmar solo tras procesar con éxito, descartar eventos más
 * antiguos ({@code occurred_at} frente a {@code last_event_at}), reintentar hasta 3 veces y enviar a la cola
 * de errores.
 *
 * <p><b>Patrones:</b> Idempotent Consumer, Retry con backoff y Dead Letter Channel.
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging;
