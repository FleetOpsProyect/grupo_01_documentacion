/**
 * Adaptador de entrada de mensajería: consumidor de réplicas (HU-DOC-10, DAS sec. 8.2).
 *
 * <p>Dos {@link co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging.SqsOwnerEventConsumer}
 * idempotentes, uno por cola propia:
 * <ul>
 * <li>{@code fleetops_document_driver_events_queue}, suscrita a {@code fleetops_driver_events} con FilterPolicy sobre
 * el Message Attribute {@code event_type}: {@code driver_activated_event} (confirmado por Conductores el 08-11-2026)
 * y {@code driver_deactivated_event} (SUPUESTO).</li>
 * <li>{@code fleetops_document_vehicle_events_queue}, suscrita a {@code fleetops_vehicle_events} con FilterPolicy
 * sobre el Message Attribute {@code event_type}: {@code vehicle_created_event} y {@code vehicle_deleted_event},
 * confirmados por Vehículos el 15-11-2026.</li>
 * </ul>
 * Ningún filtro usa el cuerpo del mensaje. El tipo de evento lo decide el Message Attribute; el cuerpo se lee tal
 * como lo publica cada productor (versión final). Ambos traducen el mensaje con
 * {@link co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging.OwnerEventMessageMapper}
 * (Anti-Corruption Layer) e invocan {@code SyncOwnerReplicaUseCase}. Borran el mensaje solo tras procesarlo; los
 * inválidos o fallidos vuelven a la cola y, con la redrive policy (maxReceiveCount = 3), pasan a su DLQ
 * ({@code ..._dlq}). Criterios completos: HU-DOC-10 en {@code docs/G01_Historias_Usuario.xlsx}.
 *
 * <p>Solo se crean con {@code fleetops.messaging.enabled=true} (perfiles {@code local} y {@code aws}). Los recursos
 * de AWS los crea {@code infra/local-aws/scripts/provision.sh}.
 *
 * <p><b>Patrones:</b> Polling Consumer, Idempotent Consumer, Retry (visibility timeout de SQS) y Dead Letter
 * Channel.
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.messaging;
