# FleetOps · Servicio de Documentación y Seguros

Microservicio de FleetOps que controla la vigencia del SOAT, la revisión tecnomecánica y la licencia de conducción, verifica que cada vehículo tenga su tarjeta de propiedad y emite alertas **antes** de que un documento venza. Sigue una **arquitectura hexagonal (puertos y adaptadores)**: el núcleo (`domain` y `application`) no conoce Spring, JPA ni AWS, y se comunica con el exterior solo mediante puertos que implementan los adaptadores de `infrastructure`. Tiene base de datos PostgreSQL propia, no llama por HTTP a otros servicios y publica sus eventos con el patrón Outbox hacia AWS SNS + SQS (DAS, secciones 3, 5 y 8).

> **Estado de esta versión:** implementa **HU-DOC-01 y HU-DOC-02**: `POST /documents` registra el SOAT, la revisión tecnomecánica y la tarjeta de propiedad de un vehículo, o la licencia de conducción de un conductor, con sus fechas de vigencia. Cada registro guarda el documento, su auditoría y sus eventos en el outbox en una sola transacción. **Todavía no usa AWS**: los eventos quedan en `outbox_events` (PENDING) y las réplicas de propietarios se cargan con datos de desarrollo.
>
> Referencias: [`context/G01_DAS_Documentos.md`](context/G01_DAS_Documentos.md) (DAS G01 v0.3) y [`docs/G01_Endpoints_Documentacion.xlsx`](docs/G01_Endpoints_Documentacion.xlsx) (contrato de endpoints y hoja «Eventos SNS» con los payloads).

---

## 1. Requisitos previos

| Herramienta | Versión |
| :-- | :-- |
| Docker Engine | 29.x o superior (probado con 29.5.3) |
| Docker Compose | v2.20 o superior |
| JDK (solo para compilar o probar sin Docker) | 21 (Temurin u Oracle) |
| Gradle | No hace falta instalarlo: se usa el wrapper (`gradlew`, Gradle 9.8.2) |

## 2. Inicio rápido con Docker

```bash
git clone <url-del-repositorio>
cd grupo_01_documentacion
cp .env.example .env        # edite DB_PASSWORD con una contraseña propia
docker compose up --build
```

Al iniciar con el volumen vacío, PostgreSQL ejecuta en orden:
1. `database/document_schema.sql`: el schema `document`, las tablas, las restricciones, los triggers y el catálogo `document_types`.
2. `database/document_seed_dev.sql`: los vehículos y conductores de prueba de las réplicas (solo para desarrollo).

El servicio espera a que la base esté sana y, al arrancar, valida que sus entidades coincidan con el esquema (`ddl-auto: validate`).

> Los scripts de `docker-entrypoint-initdb.d` **solo se ejecutan la primera vez**, con el volumen vacío. Si cambia un script, recree la base: `docker compose down -v && docker compose up --build`.

Para comprobar que el servicio está arriba:

```bash
curl -i http://localhost:8080/health
```

Si cambió `SERVICE_HOST_PORT` en `.env`, use ese puerto.

### Flujo de ejemplo (HU-DOC-01 y HU-DOC-02)

La semilla trae estos propietarios:

| Propietario | owner_id | Estado |
| :-- | :-- | :-- |
| Vehículo ABC123 | `01960000-0000-7000-8000-000000000001` | activo |
| Vehículo DEF456 | `01960000-0000-7000-8000-000000000002` | activo |
| Vehículo GHI789 | `01960000-0000-7000-8000-000000000003` | dado de baja |
| Conductor Demo Activo | `01960000-0000-7000-8000-000000000101` | activo |
| Conductor Demo Inactivo | `01960000-0000-7000-8000-000000000102` | inactivo |

Registrar el SOAT del vehículo ABC123 (responde `201 Created` con `Location: /documents/{id}`):

```bash
curl -i -X POST http://localhost:8080/documents -H "Content-Type: application/json" -d '{"document_type":"SOAT","owner_type":"VEHICLE","owner_id":"01960000-0000-7000-8000-000000000001","document_number":"AT-1300-0045-2026","issuing_entity":"Seguros Demo S.A.","issue_date":"2026-10-01","expiration_date":"2027-09-30"}'
```

Registrar su tarjeta de propiedad, que no vence:

```bash
curl -i -X POST http://localhost:8080/documents -H "Content-Type: application/json" -d '{"document_type":"PROPERTY_CARD","owner_type":"VEHICLE","owner_id":"01960000-0000-7000-8000-000000000001","document_number":"TP-998877","issuing_entity":"Transito de Cali","issue_date":"2020-05-10"}'
```

Registrar la licencia del conductor activo, con una sola categoría:

```bash
curl -i -X POST http://localhost:8080/documents -H "Content-Type: application/json" -d '{"document_type":"DRIVER_LICENSE","owner_type":"DRIVER","owner_id":"01960000-0000-7000-8000-000000000101","document_number":"LIC-1020304050","issuing_entity":"Secretaria de Movilidad de Cali","issue_date":"2024-03-01","expiration_date":"2034-03-01","license_category":"C1"}'
```

Ver los eventos que quedaron en el outbox:

```bash
docker compose exec documents-db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT event_type, status, created_at FROM document.outbox_events ORDER BY created_at"'
```

Cada registro deja un `document_registered_event`. Si el documento nace vencido, deja además un `document_expired_event`.

> **Windows (Git Bash o CMD):** la consola puede convertir las tildes de los argumentos de `curl` a cp1252, y el servicio responde `400 Solicitud mal formada`. Envíe el cuerpo desde un archivo UTF-8 con `--data-binary @documento.json`, o use PowerShell (`Invoke-RestMethod`). Los ejemplos de arriba evitan las tildes por eso.

Respuestas de error esperadas (todas en Problem Details, RFC 9457):

| Caso | Código |
| :-- | :-- |
| JSON mal formado, fecha o UUID con formato inválido | 400 |
| Campo obligatorio ausente o con formato inválido (detalle por campo en `detalles`) | 422 |
| Tipo que no corresponde al propietario, propietario inexistente o dado de baja, `expiration_date` ausente o no permitida, `license_category` ausente o donde no aplica | 422 |
| `document_type` + `document_number` repetidos, o un segundo documento activo del mismo tipo para el propietario | 409 |

Para detener: `docker compose down`. Agregue `-v` para borrar también el volumen de la base de datos.

### Revisar la base de datos con un cliente

Con el stack arriba, la base queda publicada **solo en esta máquina** (`127.0.0.1`), en el puerto `DB_HOST_PORT` de `.env` (5434 por defecto). Desde otros equipos de la red no es accesible.

| Parámetro | Valor |
| :-- | :-- |
| Host | `localhost` (o `127.0.0.1`) |
| Puerto | `5434` (`DB_HOST_PORT`) |
| Base de datos | `DB_NAME` de `.env` (`fleetops_documents`) |
| Usuario / contraseña | `DB_USER` / `DB_PASSWORD` de `.env` |
| Schema | `document` |

URL JDBC para DBeaver, IntelliJ u otro cliente: `jdbc:postgresql://localhost:5434/fleetops_documents`.

Con `psql` instalado en esta máquina:

```bash
psql -h localhost -p 5434 -U documents_app -d fleetops_documents
```

> Si ya tenía un `.env` creado antes de este cambio, agréguele la línea `DB_HOST_PORT=5434`. Sin ella, `docker compose up` falla al no poder resolver el puerto.

## 3. Ejecutar sin Docker

Se necesita un PostgreSQL 18 o superior con el esquema creado. Por ejemplo, uno temporal con los mismos scripts:

```bash
cp .env.example .env
docker run -d --name documents-db-local -e POSTGRES_DB=fleetops_documents -e POSTGRES_USER=documents_app -e POSTGRES_PASSWORD=change_me_db_password -p 5432:5432 -v "$(pwd)/database/document_schema.sql:/docker-entrypoint-initdb.d/01_document_schema.sql:ro" -v "$(pwd)/database/document_seed_dev.sql:/docker-entrypoint-initdb.d/02_document_seed_dev.sql:ro" postgres:18.6-alpine3.24
```

```bash
./gradlew bootRun
```

En Windows use `gradlew.bat bootRun`. En Git Bash, si Docker no encuentra los archivos montados, anteponga `MSYS_NO_PATHCONV=1` al `docker run`.

Los valores `POSTGRES_*` deben coincidir con `DB_NAME`, `DB_USER` y `DB_PASSWORD` de su `.env`. La aplicación lee `.env` automáticamente (`spring.config.import: optional:file:.env[.properties]`).

## 4. Pruebas y cobertura

Para ejecutar todas las pruebas:

```bash
./gradlew test
```

| Tipo | Qué cubre | Necesita |
| :-- | :-- | :-- |
| Unitarias del dominio y la aplicación | Value objects, agregado `Document`, regla única de estado (incluido el caso del 20 y 21 de octubre), eventos, `RegisterDocumentService` con todos los puertos simulados | Nada |
| Slice web (`@WebMvcTest`) | 201 + Location, snake_case, `X-Correlation-Id`, 400, 409, 422 con `detalles` | Nada |
| Unitarias de adaptadores | UUIDv7, correlación por MDC, sobre del outbox sin `document_number`, traducción de restricciones únicas a 409 | Nada |
| Integración (`DocumentRegistrationIT`) | Flujo REST → PostgreSQL 18.6 real con los scripts de `database/`, `ddl-auto: validate`, outbox, auditoría, 409 y 422 | Docker (si no está disponible, la clase se omite) |

Todas siguen el patrón AAA (Arrange → Act → Assert).

Para generar el reporte de cobertura y verificar el mínimo:

```bash
./gradlew check
```

`check` ejecuta las pruebas, genera el reporte de JaCoCo y **falla si la cobertura de líneas o de ramas de `domain` y `application` es menor al 100 %**. El reporte HTML queda en `build/reports/jacoco/test/html/index.html` y el XML en `build/reports/jacoco/test/jacocoTestReport.xml`.

Las pruebas del esquema de base de datos, independientes de Java, están en `database/document_schema_tests.sql` y terminan con `ROLLBACK`. Con el stack de Docker arriba:

```bash
docker compose exec -T documents-db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' < database/document_schema_tests.sql
```

## 5. Estructura del proyecto

```
├── build.gradle                 Spring Boot 4.1.1, Java 21, JaCoCo (100 % en el núcleo), Testcontainers
├── Dockerfile                   Multi-stage (JDK → JRE alpine), usuario no root, imágenes con versión fija
├── docker-compose.yml           PostgreSQL 18.6 que ejecuta database/*.sql al iniciar + servicio; red propia
├── .env.example                 Todas las variables documentadas
├── database/                    DDL, semilla de desarrollo y pruebas del esquema
├── docs/                        Contrato de endpoints y eventos (Excel)
└── src/main/java/co/edu/univalle/fleetops/document/service/
    ├── domain/                              NÚCLEO · Dominio (Java puro)
    │   ├── model/                           ✔ Aggregate Document · Value Objects DocumentNumber, ValidityPeriod,
    │   │                                      LicenseCategory, OwnerRef · Type Object DocumentTypeRules · enums
    │   ├── service/                         ✔ DocumentStatusCalculator: regla única de estado (ADR-DOC-01)
    │   ├── event/                           ✔ DocumentRegistered, DocumentExpired (sealed), sin document_number
    │   └── exception/                       ✔ BusinessRuleViolation (422), DomainConflict (409), ResourceNotFound (404)
    ├── application/                         NÚCLEO · Aplicación (sin Spring)
    │   ├── port/in/                         ✔ RegisterDocumentUseCase, RegisterDocumentCommand, DocumentView
    │   ├── port/out/                        ✔ Repositorios de documentos, tipos, réplicas, auditoría y outbox;
    │   │                                      BusinessClock, IdGenerator, CorrelationIdProvider
    │   └── service/                         ✔ RegisterDocumentService
    └── infrastructure/                      ADAPTADORES
        ├── adapter/in/rest/                 ✔ DocumentController (POST /documents), DTOs, mapper,
        │                                      CorrelationIdFilter, GlobalExceptionHandler (RFC 9457)
        ├── adapter/in/messaging/            Consumidor SQS de réplicas · vacío: este arquetipo no usa AWS
        ├── adapter/in/scheduler/            Job de vigencia 06:00 UTC (HU-DOC-06/07) · vacío
        ├── adapter/out/persistence/         ✔ Entidades y repositorios JPA, adaptadores, sobre del outbox
        ├── adapter/out/messaging/           Publicador SNS del outbox · vacío: este arquetipo no usa AWS
        ├── adapter/out/clock/               ✔ ZonedBusinessClock (America/Bogota)
        ├── adapter/out/id/                  ✔ UuidV7Generator (RFC 9562)
        ├── adapter/out/observability/       ✔ MdcCorrelationIdProvider
        └── config/                          ✔ CoreConfiguration (Composition Root), decorador transaccional,
                                               DocumentServiceProperties
```

✔ = implementado en esta versión. Cada paquete tiene un `package-info.java` con su rol, sus patrones y su trazabilidad al DAS.

**Regla de dependencia:** `infrastructure → application → domain`. El dominio no depende de nada.

### Flujo transaccional

```
POST /documents
 → DocumentController (Bean Validation: formato → 422)
 → TransactionalRegisterDocumentUseCase (una transacción)
 → RegisterDocumentService
     → DocumentTypeRepository (catálogo) → Document.register (reglas del tipo, estado con la fecha de Bogotá)
     → OwnerReplicaRepository (propietario activo → 422) → DocumentRepository (unicidad → 409)
     → DocumentRepository.save · AuditLogRepository (REGISTERED) · OutboxRepository (document_registered_event
       y, si nace vencido, document_expired_event)
 → COMMIT → 201 Created + Location + documento con status y days_remaining
```

## 6. Decisiones de arquitectura clave (resumen del DAS)

| ADR | Decisión |
| :-- | :-- |
| ADR-DOC-01 | El estado (VALID / EXPIRING_SOON / EXPIRED) se calcula solo por fecha y días restantes; es una regla única compartida |
| ADR-DOC-04 / 11 | Un solo documento activo por propietario y tipo (índice único parcial en la base de datos) |
| ADR-DOC-05 | Patrón Outbox: el evento se guarda en la misma transacción que el cambio |
| ADR-DOC-06 | Réplicas locales de vehículos y conductores con `is_active`; sin HTTP a otros servicios |
| ADR-DOC-07 | Una sola categoría por licencia |
| ADR-DOC-08 | Anulación lógica; la base de datos prohíbe el borrado físico |
| ADR-DOC-09 | Umbrales configurables por `ALERT_THRESHOLD_DAYS`, validados al arrancar |
| ADR-DOC-10 | Las reglas por tipo de documento viven en `document_types` (Type Object) |
| ADR-DOC-12 / 14 | Broker AWS SNS + SQS (tópico `fleetops_documents_events`, Message Attribute `event_type`). En esta versión aún no se usa |
| ADR-DOC-13 | La fecha de negocio se calcula en America/Bogota |

**Convenciones agregadas por el arquetipo** (no definidas en el DAS):
- **Problem Details (RFC 9457):** formato de error estándar de la IETF, soportado de forma nativa por Spring.
- **Composition Root explícito y decorador transaccional en `infrastructure.config`:** mantienen el núcleo libre de Spring.
- **Ids UUIDv7 asignados por la aplicación (RFC 9562):** se conocen antes de persistir (Location, `aggregate_id`) y son ordenables en el tiempo, igual que los de `uuidv7()` en PostgreSQL 18.
- **`CorrelationIdProvider` como puerto:** el núcleo obtiene el `correlation_id` sin conocer el MDC.
- **`DocumentNumber.toString()` enmascarado:** un log accidental nunca expone el número completo (DAS 4.2).
- **Sin Lombok:** se prefiere lo explícito; los objetos inmutables se modelan con `record`.

## 7. Configuración

Toda la configuración se hace con variables de entorno; vea [`.env.example`](.env.example), donde cada variable tiene su comentario. No hay credenciales en el repositorio.

## 8. Limitaciones conocidas y próximos pasos

**Limitaciones de esta versión**
- Solo HU-DOC-01 y HU-DOC-02. `Location` apunta a `GET /documents/{id}`, pero ese endpoint (HU-DOC-03) todavía no existe y responde 404.
- Sin AWS: los eventos quedan en `outbox_events` como PENDING y no se publican. Las réplicas no se alimentan por eventos, sino con `database/document_seed_dev.sql`.
- Sin migraciones: los scripts de `database/` solo corren cuando el volumen de PostgreSQL está vacío. Un cambio de esquema exige recrear la base.
- La regla «la nueva fecha de renovación debe ser futura» y las demás de HU-DOC-04/05 no aplican todavía.
- La autenticación y los roles no están definidos (DAS sec. 12, punto 15).

**Puntos abiertos del DAS (sección 12)**
- #1: `previous_document_id` en `document_renewed_event`; el equipo lo lleva al catálogo `fleetops-contracts`.
- #6: documento obligatorio ausente / Asignaciones.
- #9: reemplazo de la tarjeta de propiedad.
- #14: archivo digital.
- #15: roles y carga inicial.
- Riesgo abierto: anular un documento EXPIRED no publica evento, así que el bloqueo permanece.

**Próximos pasos sugeridos** (orden del DAS sec. 13)
1. HU-DOC-03: `GET /documents/{id}` y `GET /documents` (con el rango de vencimiento).
2. HU-DOC-10: consumidor SQS de réplicas (AWS SDK v2) y publicador SNS del outbox, con LocalStack en desarrollo.
3. HU-DOC-06/07 (job de vigencia), HU-DOC-05 (renovación), HU-DOC-04 (corrección y anulación), HU-DOC-08/09 y `validity-check`.
4. Migraciones versionadas del esquema cuando se estabilice.
5. Pipeline CI/CD y OpenAPI en `fleetops-contracts` (HU-DOC-11).
