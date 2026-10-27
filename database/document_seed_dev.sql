-- =====================================================================================================
-- FleetOps · Servicio de Documentación y Seguros
-- Datos semilla de DESARROLLO para las réplicas de propietarios (schema "document")
--
-- Fuente: DAS G01, ADR-DOC-12 y ADR-DOC-14: mientras no exista el consumidor SQS de
-- fleetops_vehicles_events y fleetops_drivers_events (SUPUESTO: nombres por confirmar con esos
-- equipos), las réplicas se cargan con datos semilla en desarrollo.
--
-- NUNCA ejecutar en producción: estos propietarios no existen en Vehículos ni en Conductores.
-- Requiere haber ejecutado antes database/document_schema.sql. Es idempotente.
--
-- Ejecución:
--   psql -h <host> -p <puerto> -U <usuario> -d <base_de_datos> -v ON_ERROR_STOP=1 -f database/document_seed_dev.sql
-- =====================================================================================================

BEGIN;

-- Vehículos: dos activos y uno dado de baja (para probar el 422 por propietario inactivo, DAS sec. 6.3)
INSERT INTO document.vehicle_replica (vehicle_id, license_plate, is_active, last_event_at)
VALUES
    ('01960000-0000-7000-8000-000000000001', 'ABC123', TRUE,  now()),
    ('01960000-0000-7000-8000-000000000002', 'DEF456', TRUE,  now()),
    ('01960000-0000-7000-8000-000000000003', 'GHI789', FALSE, now())
ON CONFLICT (vehicle_id) DO NOTHING;

-- Conductores: uno activo y uno dado de baja
INSERT INTO document.driver_replica (driver_id, full_name, is_active, last_event_at)
VALUES
    ('01960000-0000-7000-8000-000000000101', 'Conductor Demo Activo',   TRUE,  now()),
    ('01960000-0000-7000-8000-000000000102', 'Conductor Demo Inactivo', FALSE, now())
ON CONFLICT (driver_id) DO NOTHING;

COMMIT;
