-- =====================================================================================================
-- FleetOps · Servicio de Documentación y Seguros
-- Pruebas del schema "document": verifica cada restricción, índice único y trigger de
-- database/document_schema.sql contra las reglas del DAS G01 (secciones 6, 9.2 y 14).
--
-- Corre en una sola transacción y termina con ROLLBACK: no deja datos.
-- Si una prueba falla, el script se detiene con «FALLO: ...». Si todas pasan, termina con
-- «Todas las pruebas del schema pasaron».
--
-- Ejecución (después de document_schema.sql):
--   psql -h <host> -p <puerto> -U <usuario> -d <base_de_datos> -v ON_ERROR_STOP=1 -f database/document_schema_tests.sql
-- =====================================================================================================

BEGIN;

-- Ejecuta p_sql y exige que falle con la restricción p_constraint. Los cambios de p_sql se deshacen
-- (subtransacción del bloque EXCEPTION).
CREATE FUNCTION pg_temp.expect_violation(p_sql TEXT, p_constraint TEXT)
    RETURNS VOID
    LANGUAGE plpgsql
AS $$
DECLARE
    v_failed     BOOLEAN := FALSE;
    v_constraint TEXT;
    v_message    TEXT;
BEGIN
    BEGIN
        EXECUTE p_sql;
    EXCEPTION WHEN OTHERS THEN
        v_failed := TRUE;
        GET STACKED DIAGNOSTICS v_constraint = CONSTRAINT_NAME, v_message = MESSAGE_TEXT;
    END;

    IF NOT v_failed THEN
        RAISE EXCEPTION 'FALLO: se esperaba la violación de % y la sentencia se ejecutó: %', p_constraint, p_sql;
    END IF;
    IF v_constraint IS DISTINCT FROM p_constraint THEN
        RAISE EXCEPTION 'FALLO: se esperaba la violación de % y se obtuvo «%» (restricción: %)',
            p_constraint, v_message, coalesce(nullif(v_constraint, ''), 'sin nombre');
    END IF;

    RAISE NOTICE 'OK  rechaza: %', p_constraint;
END;
$$;

-- Identificadores de prueba (no colisionan con la semilla de desarrollo)
--   Vehículo 1: ...0f01 · Vehículo 2: ...0f02 · Conductor 1: ...0f11
--   SOAT v1: ...0a01 · Tecnomecánica v1: ...0a02 · Tarjeta v1: ...0a03 · Licencia c1: ...0a04
--   Renovación del SOAT v1: ...0a05

-- -----------------------------------------------------------------------------------------------------
-- 1. Casos válidos (DAS sec. 6.1)
-- -----------------------------------------------------------------------------------------------------
INSERT INTO document.documents
    (document_id, document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status, license_category)
VALUES
    ('00000000-0000-7000-8000-000000000a01', 'SOAT',                 'VEHICLE', '00000000-0000-7000-8000-000000000f01', 'TEST-SOAT-1', 'Aseguradora Demo',   '2026-01-01', '2027-01-01', 'VALID', NULL),
    ('00000000-0000-7000-8000-000000000a02', 'TECHNICAL_INSPECTION', 'VEHICLE', '00000000-0000-7000-8000-000000000f01', 'TEST-RTM-1',  'CDA Demo',           '2026-01-01', '2027-01-01', 'VALID', NULL),
    ('00000000-0000-7000-8000-000000000a03', 'PROPERTY_CARD',        'VEHICLE', '00000000-0000-7000-8000-000000000f01', 'TEST-TP-1',   'Tránsito Demo',      '2020-05-10', NULL,         'VALID', NULL),
    ('00000000-0000-7000-8000-000000000a04', 'DRIVER_LICENSE',       'DRIVER',  '00000000-0000-7000-8000-000000000f11', 'TEST-LIC-1',  'Tránsito Demo',      '2024-03-01', '2034-03-01', 'VALID', 'C1');
DO $$ BEGIN RAISE NOTICE 'OK  acepta: SOAT, tecnomecánica, tarjeta de propiedad sin vencimiento y licencia con categoría'; END $$;

-- Renovación válida (ADR-DOC-04, ADR-DOC-11): el anterior pasa a REPLACED y luego se inserta el nuevo
UPDATE document.documents SET status = 'REPLACED' WHERE document_id = '00000000-0000-7000-8000-000000000a01';
INSERT INTO document.documents
    (document_id, document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status, previous_document_id)
VALUES
    ('00000000-0000-7000-8000-000000000a05', 'SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f01', 'TEST-SOAT-2', 'Aseguradora Demo', '2026-12-15', '2028-01-01', 'VALID', '00000000-0000-7000-8000-000000000a01');
DO $$ BEGIN RAISE NOTICE 'OK  acepta: renovación del SOAT con el anterior en REPLACED'; END $$;

-- Anulación válida (ADR-DOC-08)
UPDATE document.documents
   SET status = 'CANCELLED', cancelled_at = now(), cancellation_reason = 'Registrado por error en pruebas'
 WHERE document_id = '00000000-0000-7000-8000-000000000a02';
DO $$ BEGIN RAISE NOTICE 'OK  acepta: anulación lógica con motivo'; END $$;

-- Tras anular la tecnomecánica, el vehículo puede registrar otra (ya no hay una activa)
INSERT INTO document.documents
    (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
VALUES
    ('TECHNICAL_INSPECTION', 'VEHICLE', '00000000-0000-7000-8000-000000000f01', 'TEST-RTM-2', 'CDA Demo', '2026-02-01', '2027-02-01', 'VALID');
DO $$ BEGIN RAISE NOTICE 'OK  acepta: nueva tecnomecánica tras anular la anterior'; END $$;

-- El número de un documento anulado queda libre: se registra de nuevo para otro propietario (DAS sec. 6.3, v0.4)
INSERT INTO document.documents
    (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
VALUES
    ('TECHNICAL_INSPECTION', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', 'TEST-RTM-1', 'CDA Demo', '2026-01-01', '2027-01-01', 'VALID');
DO $$ BEGIN RAISE NOTICE 'OK  acepta: mismo tipo + número de un documento anulado'; END $$;

-- -----------------------------------------------------------------------------------------------------
-- 2. documents: unicidad (DAS sec. 6.3, ADR-DOC-04, ADR-DOC-11)
-- -----------------------------------------------------------------------------------------------------
-- TEST-SOAT-1 está en REPLACED: un documento reemplazado sigue reservando su número
SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
    VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', 'TEST-SOAT-1', 'Otra', '2026-01-01', '2027-01-01', 'VALID')
$q$, 'uk_documents_type_number');

-- TEST-RTM-2 está activo (VALID) para otro vehículo
SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
    VALUES ('TECHNICAL_INSPECTION', 'VEHICLE', '00000000-0000-7000-8000-000000000f03', 'TEST-RTM-2', 'Otra', '2026-01-01', '2027-01-01', 'VALID')
$q$, 'uk_documents_type_number');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
    VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f01', 'TEST-SOAT-3', 'Otra', '2026-01-01', '2027-01-01', 'EXPIRED')
$q$, 'uk_documents_one_active_per_owner_type');

-- Segundo sucesor del mismo documento (se inserta CANCELLED para no chocar con el índice de activos)
SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status,
                                    previous_document_id, cancelled_at, cancellation_reason)
    VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f01', 'TEST-SOAT-4', 'Otra', '2026-12-15', '2028-06-01', 'CANCELLED',
            '00000000-0000-7000-8000-000000000a01', now(), 'Segundo sucesor de prueba')
$q$, 'uk_documents_previous_document');

-- -----------------------------------------------------------------------------------------------------
-- 3. documents: reglas por tipo (ADR-DOC-10) y propietario del tipo
-- -----------------------------------------------------------------------------------------------------
SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status, license_category)
    VALUES ('SOAT', 'DRIVER', '00000000-0000-7000-8000-000000000f11', 'TEST-SOAT-5', 'Otra', '2026-01-01', '2027-01-01', 'VALID', 'B1')
$q$, 'fk_documents_document_type');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
    VALUES ('TECHNICAL_INSPECTION', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', 'TEST-RTM-3', 'CDA', '2026-01-01', NULL, 'VALID')
$q$, 'ck_documents_expiration_required');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
    VALUES ('PROPERTY_CARD', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', 'TEST-TP-2', 'Tránsito', '2026-01-01', '2030-01-01', 'VALID')
$q$, 'ck_documents_expiration_not_allowed');

SELECT pg_temp.expect_violation($q$
    DO $d$ BEGIN
        UPDATE document.document_types SET is_active = FALSE WHERE code = 'SOAT';
        INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
        VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', 'TEST-SOAT-6', 'Otra', '2026-01-01', '2027-01-01', 'VALID');
    END $d$
$q$, 'ck_documents_type_active');

-- La tarjeta de propiedad no se renueva (DAS sec. 6.2)
SELECT pg_temp.expect_violation($q$
    DO $d$ BEGIN
        UPDATE document.documents SET status = 'REPLACED' WHERE document_id = '00000000-0000-7000-8000-000000000a03';
        INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status, previous_document_id)
        VALUES ('PROPERTY_CARD', 'VEHICLE', '00000000-0000-7000-8000-000000000f01', 'TEST-TP-3', 'Tránsito', '2026-01-01', NULL, 'VALID',
                '00000000-0000-7000-8000-000000000a03');
    END $d$
$q$, 'ck_documents_type_renewable');

-- -----------------------------------------------------------------------------------------------------
-- 4. documents: validaciones de campos (DAS sec. 6.2 y 6.3, ADR-DOC-07, ADR-DOC-08)
-- -----------------------------------------------------------------------------------------------------
SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
    VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', 'TEST-SOAT-7', 'Otra', '2026-01-01', '2026-01-01', 'VALID')
$q$, 'ck_documents_expiration_after_issue');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
    VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', 'TEST-SOAT-8', 'Otra', '2026-01-01', '2027-01-01', 'ACTIVE')
$q$, 'ck_documents_status');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
    VALUES ('DRIVER_LICENSE', 'DRIVER', '00000000-0000-7000-8000-000000000f12', 'TEST-LIC-2', 'Tránsito', '2026-01-01', '2036-01-01', 'VALID')
$q$, 'ck_documents_license_category');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status, license_category)
    VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', 'TEST-SOAT-9', 'Otra', '2026-01-01', '2027-01-01', 'VALID', 'B1')
$q$, 'ck_documents_license_category');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status, license_category)
    VALUES ('DRIVER_LICENSE', 'DRIVER', '00000000-0000-7000-8000-000000000f12', 'TEST-LIC-3', 'Tránsito', '2026-01-01', '2036-01-01', 'VALID', 'B1,C1')
$q$, 'ck_documents_license_category');

SELECT pg_temp.expect_violation($q$
    UPDATE document.documents SET status = 'CANCELLED', cancelled_at = now(), cancellation_reason = 'corto'
     WHERE document_id = '00000000-0000-7000-8000-000000000a04'
$q$, 'ck_documents_cancellation');

SELECT pg_temp.expect_violation($q$
    UPDATE document.documents SET status = 'CANCELLED' WHERE document_id = '00000000-0000-7000-8000-000000000a04'
$q$, 'ck_documents_cancellation');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
    VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', '   ', 'Otra', '2026-01-01', '2027-01-01', 'VALID')
$q$, 'ck_documents_document_number_not_blank');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status)
    VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', 'TEST-SOAT-10', '', '2026-01-01', '2027-01-01', 'VALID')
$q$, 'ck_documents_issuing_entity_not_blank');

-- -----------------------------------------------------------------------------------------------------
-- 5. documents: renovación (ADR-DOC-04, ADR-DOC-11, DAS sec. 6.3)
-- -----------------------------------------------------------------------------------------------------
-- El anterior sigue activo (no se pasó a REPLACED)
SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status, previous_document_id)
    VALUES ('DRIVER_LICENSE', 'DRIVER', '00000000-0000-7000-8000-000000000f11', 'TEST-LIC-4', 'Tránsito', '2034-01-01', '2044-01-01', 'VALID',
            '00000000-0000-7000-8000-000000000a04')
$q$, 'ck_documents_renewal_previous_replaced');

-- Cambia el propietario
SELECT pg_temp.expect_violation($q$
    INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status, previous_document_id)
    VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f02', 'TEST-SOAT-11', 'Otra', '2026-12-15', '2028-01-01', 'VALID',
            '00000000-0000-7000-8000-000000000a01')
$q$, 'ck_documents_renewal_same_owner_type');

-- La nueva expiration_date no es posterior a la del anterior
SELECT pg_temp.expect_violation($q$
    DO $d$ BEGIN
        UPDATE document.documents SET status = 'REPLACED' WHERE document_id = '00000000-0000-7000-8000-000000000a05';
        INSERT INTO document.documents (document_type, owner_type, owner_id, document_number, issuing_entity, issue_date, expiration_date, status, previous_document_id)
        VALUES ('SOAT', 'VEHICLE', '00000000-0000-7000-8000-000000000f01', 'TEST-SOAT-12', 'Otra', '2027-01-01', '2028-01-01', 'VALID',
                '00000000-0000-7000-8000-000000000a05');
    END $d$
$q$, 'ck_documents_renewal_expiration_later');

SELECT pg_temp.expect_violation($q$
    UPDATE document.documents SET previous_document_id = document_id WHERE document_id = '00000000-0000-7000-8000-000000000a04'
$q$, 'ck_documents_immutable_fields');

-- -----------------------------------------------------------------------------------------------------
-- 6. documents: integridad (DAS sec. 4.2 y 6.2, ADR-DOC-08)
-- -----------------------------------------------------------------------------------------------------
SELECT pg_temp.expect_violation($q$
    UPDATE document.documents SET owner_id = '00000000-0000-7000-8000-000000000f02' WHERE document_id = '00000000-0000-7000-8000-000000000a05'
$q$, 'ck_documents_immutable_fields');

SELECT pg_temp.expect_violation($q$
    UPDATE document.documents SET document_type = 'TECHNICAL_INSPECTION' WHERE document_id = '00000000-0000-7000-8000-000000000a05'
$q$, 'ck_documents_immutable_fields');

SELECT pg_temp.expect_violation($q$
    UPDATE document.documents SET issuing_entity = 'Cambio' WHERE document_id = '00000000-0000-7000-8000-000000000a01'
$q$, 'ck_documents_final_status');

SELECT pg_temp.expect_violation($q$
    UPDATE document.documents SET status = 'VALID', cancelled_at = NULL, cancellation_reason = NULL
     WHERE document_id = '00000000-0000-7000-8000-000000000a02'
$q$, 'ck_documents_final_status');

SELECT pg_temp.expect_violation($q$
    DELETE FROM document.documents WHERE document_id = '00000000-0000-7000-8000-000000000a05'
$q$, 'ck_documents_no_physical_delete');

SELECT pg_temp.expect_violation($q$
    UPDATE document.documents SET version = -1 WHERE document_id = '00000000-0000-7000-8000-000000000a05'
$q$, 'ck_documents_version');

-- -----------------------------------------------------------------------------------------------------
-- 7. Catálogo, outbox, alertas, job, auditoría y réplicas
-- -----------------------------------------------------------------------------------------------------
SELECT pg_temp.expect_violation($q$
    INSERT INTO document.document_types (code, name, owner_type, has_expiration, is_mandatory, is_renewable)
    VALUES ('TEST_TYPE', 'Prueba', 'VEHICLE', FALSE, FALSE, TRUE)
$q$, 'ck_document_types_renewable_requires_expiration');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.document_types (code, name, owner_type, has_expiration, is_mandatory, is_renewable)
    VALUES ('TEST_TYPE', 'Prueba', 'COMPANY', TRUE, FALSE, TRUE)
$q$, 'ck_document_types_owner_type');

INSERT INTO document.outbox_events (event_id, event_type, topic, message_key, aggregate_id, payload)
VALUES ('00000000-0000-7000-8000-000000000e01', 'document_renewed_event', 'fleetops_documents_events',
        '00000000-0000-7000-8000-000000000f01', '00000000-0000-7000-8000-000000000a05', '{"event_type": "document_renewed_event"}');
DO $$ BEGIN RAISE NOTICE 'OK  acepta: evento PENDING en el outbox'; END $$;

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.outbox_events (event_id, event_type, topic, message_key, aggregate_id, payload)
    VALUES (uuidv7(), 'document_deleted_event', 't', 'k', '00000000-0000-7000-8000-000000000a05', '{}')
$q$, 'ck_outbox_events_event_type');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.outbox_events (event_id, event_type, topic, message_key, aggregate_id, payload, status)
    VALUES (uuidv7(), 'document_expired_event', 't', 'k', '00000000-0000-7000-8000-000000000a05', '{}', 'PUBLISHED')
$q$, 'ck_outbox_events_published');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.outbox_events (event_id, event_type, topic, message_key, aggregate_id, payload)
    VALUES (uuidv7(), 'document_expired_event', 't', 'k', uuidv7(), '{}')
$q$, 'fk_outbox_events_document');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.outbox_events (event_id, event_type, topic, message_key, aggregate_id, payload)
    VALUES (uuidv7(), 'document_expired_event', 't', 'k', '00000000-0000-7000-8000-000000000a05', '[]')
$q$, 'ck_outbox_events_payload_object');

INSERT INTO document.issued_alerts (document_id, threshold_days, published, outbox_event_id)
VALUES ('00000000-0000-7000-8000-000000000a05', 30, FALSE, NULL);
DO $$ BEGIN RAISE NOTICE 'OK  acepta: umbral omitido sin evento (ADR-DOC-03)'; END $$;

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.issued_alerts (document_id, threshold_days, published, outbox_event_id)
    VALUES ('00000000-0000-7000-8000-000000000a05', 30, TRUE, '00000000-0000-7000-8000-000000000e01')
$q$, 'uk_issued_alerts_document_threshold');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.issued_alerts (document_id, threshold_days, published, outbox_event_id)
    VALUES ('00000000-0000-7000-8000-000000000a05', 15, TRUE, NULL)
$q$, 'ck_issued_alerts_published_event');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.issued_alerts (document_id, threshold_days, published, outbox_event_id)
    VALUES ('00000000-0000-7000-8000-000000000a05', -1, FALSE, NULL)
$q$, 'ck_issued_alerts_threshold');

INSERT INTO document.scheduled_job_runs (job_name, business_date, status, instance_id)
VALUES ('document-expiration-check', '2026-10-20', 'RUNNING', 'instance-test');
DO $$ BEGIN RAISE NOTICE 'OK  acepta: ejecución RUNNING del job'; END $$;

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.scheduled_job_runs (job_name, business_date, status, instance_id)
    VALUES ('document-expiration-check', '2026-10-20', 'RUNNING', 'instance-other')
$q$, 'uk_scheduled_job_runs_job_date');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.scheduled_job_runs (job_name, business_date, status, instance_id)
    VALUES ('document-expiration-check', '2026-10-21', 'COMPLETED', 'instance-test')
$q$, 'ck_scheduled_job_runs_finished');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.document_audit_log (document_id, action, changes)
    VALUES ('00000000-0000-7000-8000-000000000a05', 'DELETED', '{}')
$q$, 'ck_document_audit_log_action');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.document_audit_log (document_id, action, changes)
    VALUES ('00000000-0000-7000-8000-000000000a05', 'CORRECTED', '"texto"')
$q$, 'ck_document_audit_log_changes_object');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.vehicle_replica (vehicle_id, license_plate, last_event_at) VALUES (uuidv7(), ' ', now())
$q$, 'ck_vehicle_replica_license_plate');

SELECT pg_temp.expect_violation($q$
    INSERT INTO document.driver_replica (driver_id, full_name, last_event_at) VALUES (uuidv7(), '', now())
$q$, 'ck_driver_replica_full_name');

DO $$ BEGIN RAISE NOTICE 'Todas las pruebas del schema pasaron'; END $$;

ROLLBACK;
