-- =====================================================================================================
-- FleetOps · Servicio de Documentación y Seguros
-- Script de creación del schema "document" (PostgreSQL 18+)
--
-- Fuente: DAS G01 (context/G01_DAS_Documentos.md), secciones 6 (modelo de dominio), 6.4 (datos propios),
--         9.2 (ADR-DOC-01 a 12) y 14 (MER).
--
-- Script idempotente: se puede ejecutar varias veces sin error ni duplicados. Corre en una sola transacción.
-- Todavía no se usan migraciones: este archivo es la referencia del esquema.
--
-- Ejecución:
--   psql -h <host> -p <puerto> -U <usuario> -d <base_de_datos> -v ON_ERROR_STOP=1 -f database/document_schema.sql
-- =====================================================================================================

BEGIN;

CREATE SCHEMA IF NOT EXISTS document;

COMMENT ON SCHEMA document IS
    'Servicio de Documentación y Seguros (FleetOps): base de datos propia; ningún otro servicio accede (DAS sec. 5).';

-- =====================================================================================================
-- 1. CATÁLOGO DE TIPOS DE DOCUMENTO (ADR-DOC-10)
-- =====================================================================================================
CREATE TABLE IF NOT EXISTS document.document_types (
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    owner_type      VARCHAR(10)  NOT NULL,
    has_expiration  BOOLEAN      NOT NULL,
    is_mandatory    BOOLEAN      NOT NULL,
    is_renewable    BOOLEAN      NOT NULL,
    single_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_document_types PRIMARY KEY (code),
    -- Clave alterna: permite que documents valide en una sola FK que el propietario corresponde al tipo
    CONSTRAINT uk_document_types_code_owner UNIQUE (code, owner_type),
    CONSTRAINT ck_document_types_code CHECK (code ~ '^[A-Z][A-Z_]*$'),
    CONSTRAINT ck_document_types_owner_type CHECK (owner_type IN ('VEHICLE', 'DRIVER')),
    -- Un documento que no vence no se renueva (DAS sec. 6.2: tarjeta de propiedad)
    CONSTRAINT ck_document_types_renewable_requires_expiration CHECK (has_expiration OR NOT is_renewable)
);

COMMENT ON TABLE  document.document_types IS 'Catálogo de los tipos de documento y sus reglas (DAS sec. 6.1, ADR-DOC-10).';
COMMENT ON COLUMN document.document_types.has_expiration IS 'FALSE solo para PROPERTY_CARD; determina si el job evalúa el tipo.';
COMMENT ON COLUMN document.document_types.is_mandatory   IS 'Obligatorio para operar (cumplimiento, HU-DOC-09).';
COMMENT ON COLUMN document.document_types.is_renewable   IS 'Admite renovación (HU-DOC-05); FALSE para PROPERTY_CARD.';
COMMENT ON COLUMN document.document_types.single_active  IS 'Como máximo un documento activo por propietario y tipo (ADR-DOC-11).';
COMMENT ON COLUMN document.document_types.is_active      IS 'Tipo habilitado en el catálogo; no se registran documentos de tipos inactivos.';

-- =====================================================================================================
-- 2. DOCUMENTOS (DAS sec. 6.1 a 6.3)
-- =====================================================================================================
CREATE TABLE IF NOT EXISTS document.documents (
    document_id           UUID         NOT NULL DEFAULT uuidv7(),
    document_type         VARCHAR(30)  NOT NULL,
    owner_type            VARCHAR(10)  NOT NULL,
    owner_id              UUID         NOT NULL,
    document_number       VARCHAR(50)  NOT NULL,
    issuing_entity        VARCHAR(150) NOT NULL,
    issue_date            DATE         NOT NULL,
    expiration_date       DATE,
    status                VARCHAR(20)  NOT NULL,
    license_category      VARCHAR(5),
    previous_document_id  UUID,
    cancellation_reason   VARCHAR(200),
    cancelled_at          TIMESTAMPTZ,
    version               INTEGER      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_documents PRIMARY KEY (document_id),
    -- El tipo existe y el propietario corresponde al tipo (VEHICLE o DRIVER)
    CONSTRAINT fk_documents_document_type FOREIGN KEY (document_type, owner_type)
        REFERENCES document.document_types (code, owner_type),
    -- Cadena de renovaciones (ADR-DOC-04): un documento se renueva como máximo una vez
    CONSTRAINT fk_documents_previous_document FOREIGN KEY (previous_document_id)
        REFERENCES document.documents (document_id),
    CONSTRAINT uk_documents_previous_document UNIQUE (previous_document_id),
    -- La combinación tipo + número es única (DAS sec. 6.3)
    CONSTRAINT uk_documents_type_number UNIQUE (document_type, document_number),

    CONSTRAINT ck_documents_owner_type CHECK (owner_type IN ('VEHICLE', 'DRIVER')),
    CONSTRAINT ck_documents_status CHECK (status IN ('VALID', 'EXPIRING_SOON', 'EXPIRED', 'REPLACED', 'CANCELLED')),
    CONSTRAINT ck_documents_document_number_not_blank CHECK (btrim(document_number) <> ''),
    CONSTRAINT ck_documents_issuing_entity_not_blank CHECK (btrim(issuing_entity) <> ''),
    -- expiration_date posterior a issue_date (DAS sec. 6.3)
    CONSTRAINT ck_documents_expiration_after_issue CHECK (expiration_date IS NULL OR expiration_date > issue_date),
    -- Una sola categoría por licencia, obligatoria en DRIVER_LICENSE y vacía en documentos de vehículo (ADR-DOC-07)
    CONSTRAINT ck_documents_license_category CHECK (
        (owner_type = 'DRIVER'  AND license_category IS NOT NULL AND license_category ~ '^[A-Z][0-9]?$')
     OR (owner_type = 'VEHICLE' AND license_category IS NULL)
    ),
    -- Anulación lógica con motivo de 10 a 200 caracteres (ADR-DOC-08, HU-DOC-04)
    CONSTRAINT ck_documents_cancellation CHECK (
        (status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND cancellation_reason IS NOT NULL
            AND char_length(btrim(cancellation_reason)) BETWEEN 10 AND 200)
     OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancellation_reason IS NULL)
    ),
    CONSTRAINT ck_documents_not_self_renewal CHECK (previous_document_id IS NULL OR previous_document_id <> document_id),
    CONSTRAINT ck_documents_version CHECK (version >= 0),
    CONSTRAINT ck_documents_timestamps CHECK (updated_at >= created_at)
);

COMMENT ON TABLE  document.documents IS 'Documentos de vehículos y conductores con su historial de renovaciones (DAS sec. 6).';
COMMENT ON COLUMN document.documents.owner_id IS 'Referencia lógica a vehicle_replica o driver_replica según owner_type (sin FK, ADR-DOC-06).';
COMMENT ON COLUMN document.documents.document_number IS 'Dato personal en licencias: se enmascara en logs y no viaja en eventos (DAS sec. 4.2).';
COMMENT ON COLUMN document.documents.expiration_date IS 'Vacía solo en tipos sin vencimiento (PROPERTY_CARD).';
COMMENT ON COLUMN document.documents.status IS 'Estado guardado; VALID/EXPIRING_SOON/EXPIRED se recalculan por fecha en cada consulta (ADR-DOC-01).';
COMMENT ON COLUMN document.documents.license_category IS 'Solo DRIVER_LICENSE: una única categoría por licencia (ADR-DOC-07).';
COMMENT ON COLUMN document.documents.version IS 'Control de concurrencia optimista (ADR-DOC-04).';

-- Un solo documento activo por propietario y tipo (ADR-DOC-11).
-- Se considera activo un documento en VALID, EXPIRING_SOON o EXPIRED (DAS sec. 6.2).
CREATE UNIQUE INDEX IF NOT EXISTS uk_documents_one_active_per_owner_type
    ON document.documents (owner_type, owner_id, document_type)
    WHERE status IN ('VALID', 'EXPIRING_SOON', 'EXPIRED');

-- Consultas por propietario (HU-DOC-03 y HU-DOC-09)
CREATE INDEX IF NOT EXISTS ix_documents_owner
    ON document.documents (owner_type, owner_id);

-- Job de vigencia y próximos a vencer (HU-DOC-06/07/08): solo documentos activos con vencimiento
CREATE INDEX IF NOT EXISTS ix_documents_active_expiration
    ON document.documents (expiration_date)
    WHERE expiration_date IS NOT NULL AND status IN ('VALID', 'EXPIRING_SOON', 'EXPIRED');

-- Reglas que dependen del catálogo (ADR-DOC-10). Se leen de document_types en lugar de fijar los tipos en
-- condicionales: si vence, si se renueva y si el tipo está habilitado.
CREATE OR REPLACE FUNCTION document.fn_documents_check_type_rules()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
DECLARE
    v_type document.document_types%ROWTYPE;
BEGIN
    SELECT * INTO v_type FROM document.document_types WHERE code = NEW.document_type;
    IF NOT FOUND THEN
        -- La FK fk_documents_document_type rechaza el registro
        RETURN NEW;
    END IF;

    IF TG_OP = 'INSERT' AND NOT v_type.is_active THEN
        RAISE EXCEPTION 'El tipo de documento % no está habilitado', NEW.document_type
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_documents_type_active';
    END IF;

    IF v_type.has_expiration AND NEW.expiration_date IS NULL THEN
        RAISE EXCEPTION 'expiration_date es obligatoria para el tipo %', NEW.document_type
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_documents_expiration_required';
    END IF;

    IF NOT v_type.has_expiration AND NEW.expiration_date IS NOT NULL THEN
        RAISE EXCEPTION 'El tipo % no tiene vencimiento: expiration_date debe ir vacía', NEW.document_type
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_documents_expiration_not_allowed';
    END IF;

    IF NEW.previous_document_id IS NOT NULL AND NOT v_type.is_renewable THEN
        RAISE EXCEPTION 'El tipo % no admite renovación', NEW.document_type
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_documents_type_renewable';
    END IF;

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION document.fn_documents_check_type_rules() IS
    'Valida las reglas por tipo leídas del catálogo document_types (ADR-DOC-10).';

CREATE OR REPLACE TRIGGER trg_documents_check_type_rules
    BEFORE INSERT OR UPDATE OF document_type, expiration_date, previous_document_id
    ON document.documents
    FOR EACH ROW
    EXECUTE FUNCTION document.fn_documents_check_type_rules();

-- =====================================================================================================
-- 3. AUDITORÍA DE DOCUMENTOS (DAS sec. 4.2 «Responsabilidad», sec. 6.4)
-- =====================================================================================================
CREATE TABLE IF NOT EXISTS document.document_audit_log (
    id              UUID         NOT NULL DEFAULT uuidv7(),
    document_id     UUID         NOT NULL,
    action          VARCHAR(30)  NOT NULL,
    changes         JSONB        NOT NULL,
    reason          VARCHAR(200),
    correlation_id  UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_document_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_document_audit_log_document FOREIGN KEY (document_id)
        REFERENCES document.documents (document_id),
    CONSTRAINT ck_document_audit_log_action CHECK (
        action IN ('REGISTERED', 'CORRECTED', 'RENEWED', 'REPLACED', 'CANCELLED', 'STATUS_CHANGED')
    ),
    CONSTRAINT ck_document_audit_log_changes_object CHECK (jsonb_typeof(changes) = 'object')
);

COMMENT ON TABLE  document.document_audit_log IS
    'Registro de cada cambio sobre un documento. Uso interno: document_number se guarda completo y nunca se expone (DAS sec. 6.4).';
COMMENT ON COLUMN document.document_audit_log.changes IS 'Valores antes y después del cambio.';

CREATE INDEX IF NOT EXISTS ix_document_audit_log_document
    ON document.document_audit_log (document_id, created_at);

-- =====================================================================================================
-- 4. OUTBOX DE EVENTOS (ADR-DOC-05, ADR-DOC-12)
-- =====================================================================================================
CREATE TABLE IF NOT EXISTS document.outbox_events (
    event_id        UUID          NOT NULL,
    event_type      VARCHAR(60)   NOT NULL,
    topic           VARCHAR(150)  NOT NULL,
    message_key     VARCHAR(36)   NOT NULL,
    aggregate_id    UUID          NOT NULL,
    payload         JSONB         NOT NULL,
    correlation_id  UUID,
    status          VARCHAR(15)   NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER       NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,

    CONSTRAINT pk_outbox_events PRIMARY KEY (event_id),
    CONSTRAINT fk_outbox_events_document FOREIGN KEY (aggregate_id)
        REFERENCES document.documents (document_id),
    CONSTRAINT ck_outbox_events_event_type CHECK (
        event_type IN ('document.registered', 'document.expiring_soon', 'document.expired', 'document.renewed')
    ),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_events_published CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL)),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_events_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

COMMENT ON TABLE  document.outbox_events IS
    'Eventos guardados en la misma transacción que el cambio (ADR-DOC-05). No se publican hasta definir el broker (ADR-DOC-12).';
COMMENT ON COLUMN document.outbox_events.event_id    IS 'event_id del sobre; determinista en los eventos del job (DAS sec. 8.1).';
COMMENT ON COLUMN document.outbox_events.message_key IS 'owner_id: clave de partición del topic (DAS sec. 5).';
COMMENT ON COLUMN document.outbox_events.payload     IS 'Sobre estándar completo; nunca incluye document_number (DAS sec. 4.2).';

-- Lectura del publicador: eventos pendientes en orden de creación
CREATE INDEX IF NOT EXISTS ix_outbox_events_pending
    ON document.outbox_events (created_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS ix_outbox_events_aggregate
    ON document.outbox_events (aggregate_id);

-- =====================================================================================================
-- 5. ALERTAS EMITIDAS (HU-DOC-06, ADR-DOC-02, ADR-DOC-03)
-- =====================================================================================================
CREATE TABLE IF NOT EXISTS document.issued_alerts (
    id               UUID         NOT NULL DEFAULT uuidv7(),
    document_id      UUID         NOT NULL,
    threshold_days   INTEGER      NOT NULL,
    published        BOOLEAN      NOT NULL,
    outbox_event_id  UUID,
    issued_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_issued_alerts PRIMARY KEY (id),
    CONSTRAINT fk_issued_alerts_document FOREIGN KEY (document_id)
        REFERENCES document.documents (document_id),
    CONSTRAINT fk_issued_alerts_outbox_event FOREIGN KEY (outbox_event_id)
        REFERENCES document.outbox_events (event_id),
    -- Garantía contra avisos duplicados con varias instancias o reejecuciones (ADR-DOC-02)
    CONSTRAINT uk_issued_alerts_document_threshold UNIQUE (document_id, threshold_days),
    CONSTRAINT ck_issued_alerts_threshold CHECK (threshold_days >= 0),
    -- Un umbral omitido (ADR-DOC-03) se marca como emitido sin evento
    CONSTRAINT ck_issued_alerts_published_event CHECK (published = (outbox_event_id IS NOT NULL))
);

COMMENT ON TABLE  document.issued_alerts IS 'Umbrales ya emitidos por documento (DAS sec. 6.4).';
COMMENT ON COLUMN document.issued_alerts.published IS 'FALSE si el umbral se omitió por ADR-DOC-03 (se emitió solo el menor).';

-- =====================================================================================================
-- 6. EJECUCIONES DEL JOB (HU-DOC-06/07, ADR-DOC-02)
-- =====================================================================================================
CREATE TABLE IF NOT EXISTS document.scheduled_job_runs (
    id               UUID          NOT NULL DEFAULT uuidv7(),
    job_name         VARCHAR(60)   NOT NULL,
    business_date    DATE          NOT NULL,
    status           VARCHAR(15)   NOT NULL,
    instance_id      VARCHAR(100)  NOT NULL,
    evaluated_count  INTEGER       NOT NULL DEFAULT 0,
    issued_count     INTEGER       NOT NULL DEFAULT 0,
    failed_count     INTEGER       NOT NULL DEFAULT 0,
    started_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,

    CONSTRAINT pk_scheduled_job_runs PRIMARY KEY (id),
    -- Una ejecución por job y fecha de negocio: evita la doble ejecución entre instancias
    CONSTRAINT uk_scheduled_job_runs_job_date UNIQUE (job_name, business_date),
    CONSTRAINT ck_scheduled_job_runs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_scheduled_job_runs_counts CHECK (evaluated_count >= 0 AND issued_count >= 0 AND failed_count >= 0),
    CONSTRAINT ck_scheduled_job_runs_finished CHECK (
        (status = 'RUNNING' AND finished_at IS NULL)
     OR (status <> 'RUNNING' AND finished_at IS NOT NULL AND finished_at >= started_at)
    )
);

COMMENT ON TABLE document.scheduled_job_runs IS
    'Ejecuciones del job por fecha de negocio: recuperación al arrancar y monitoreo (DAS sec. 6.4).';

-- =====================================================================================================
-- 7. EVENTOS CONSUMIDOS (HU-DOC-10: idempotencia del consumidor)
-- =====================================================================================================
CREATE TABLE IF NOT EXISTS document.processed_events (
    event_id      UUID          NOT NULL,
    event_type    VARCHAR(60)   NOT NULL,
    source        VARCHAR(150)  NOT NULL,
    processed_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);

COMMENT ON TABLE document.processed_events IS
    'event_id de los eventos consumidos; se guarda en la misma transacción que la réplica (DAS sec. 8.2).';

-- =====================================================================================================
-- 8. RÉPLICAS LOCALES DE PROPIETARIOS (HU-DOC-10, ADR-DOC-06)
-- =====================================================================================================
CREATE TABLE IF NOT EXISTS document.vehicle_replica (
    vehicle_id     UUID          NOT NULL,
    license_plate  VARCHAR(10)   NOT NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    last_event_at  TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_vehicle_replica PRIMARY KEY (vehicle_id),
    CONSTRAINT ck_vehicle_replica_license_plate CHECK (btrim(license_plate) <> '')
);

COMMENT ON TABLE  document.vehicle_replica IS 'Réplica mínima de Vehículos alimentada por eventos (DAS sec. 6.4).';
COMMENT ON COLUMN document.vehicle_replica.is_active     IS 'FALSE tras vehicle.decommissioned: deja de ser propietario válido.';
COMMENT ON COLUMN document.vehicle_replica.last_event_at IS 'occurred_at del último evento aplicado; descarta eventos más antiguos.';

CREATE TABLE IF NOT EXISTS document.driver_replica (
    driver_id      UUID          NOT NULL,
    full_name      VARCHAR(150)  NOT NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    last_event_at  TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_driver_replica PRIMARY KEY (driver_id),
    CONSTRAINT ck_driver_replica_full_name CHECK (btrim(full_name) <> '')
);

COMMENT ON TABLE  document.driver_replica IS 'Réplica mínima de Conductores alimentada por eventos (DAS sec. 6.4).';
COMMENT ON COLUMN document.driver_replica.is_active     IS 'FALSE tras driver.deactivated: deja de ser propietario válido.';
COMMENT ON COLUMN document.driver_replica.last_event_at IS 'occurred_at del último evento aplicado; descarta eventos más antiguos.';

-- =====================================================================================================
-- 9. DATOS DE CATÁLOGO (DAS sec. 6.1)
-- =====================================================================================================
INSERT INTO document.document_types
    (code, name, owner_type, has_expiration, is_mandatory, is_renewable, single_active, is_active)
VALUES
    ('SOAT',                 'SOAT',                      'VEHICLE', TRUE,  TRUE, TRUE,  TRUE, TRUE),
    ('TECHNICAL_INSPECTION', 'Revisión tecnomecánica',    'VEHICLE', TRUE,  TRUE, TRUE,  TRUE, TRUE),
    ('PROPERTY_CARD',        'Tarjeta de propiedad',      'VEHICLE', FALSE, TRUE, FALSE, TRUE, TRUE),
    ('DRIVER_LICENSE',       'Licencia de conducción',    'DRIVER',  TRUE,  TRUE, TRUE,  TRUE, TRUE)
ON CONFLICT (code) DO UPDATE SET
    name           = EXCLUDED.name,
    owner_type     = EXCLUDED.owner_type,
    has_expiration = EXCLUDED.has_expiration,
    is_mandatory   = EXCLUDED.is_mandatory,
    is_renewable   = EXCLUDED.is_renewable,
    single_active  = EXCLUDED.single_active,
    is_active      = EXCLUDED.is_active;

COMMIT;
