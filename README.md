# FleetOps · Servicio de Documentación y Seguros

Microservicio de FleetOps que controla la vigencia del SOAT, la revisión tecnomecánica y la licencia de conducción, verifica que cada vehículo tenga su tarjeta de propiedad y emite alertas **antes** de que un documento venza. Sigue una **arquitectura hexagonal (puertos y adaptadores)**: el núcleo (`domain` y `application`) no conoce Spring, JPA ni el broker de mensajería. Se comunica con el exterior solo mediante puertos, que implementan los adaptadores de `infrastructure`. Tiene base de datos PostgreSQL propia y no llama por HTTP a otros servicios (DAS, secciones 3 y 5).

> **Estado de esta versión:** es solo la **definición base** del arquetipo: estructura de carpetas, capas y capacidades transversales operativas. Todavía no incluye lógica de negocio, migraciones de base de datos ni pruebas unitarias.
>
> Referencia: [`context/G01_DAS_Documentos.md`](context/G01_DAS_Documentos.md) (DAS G01).

---

## 1. Requisitos previos

| Herramienta | Versión |
| :-- | :-- |
| Docker Engine | 29.x o superior |
| Docker Compose | v2.20 o superior (probado con v5.1.4) |
| JDK (solo para ejecutar sin Docker) | 21 (Temurin u Oracle) |
| Gradle | No hace falta instalarlo: se usa el wrapper (`gradlew`, Gradle 9.8.2) |

## 2. Inicio rápido con Docker

```bash
git clone <url-del-repositorio>
cd grupo_01_documentacion
cp .env.example .env        # edite DB_PASSWORD con una contraseña propia
docker compose up --build
```

Verificación:

```bash
curl -i http://localhost:8080/health
# HTTP/1.1 200 · X-Correlation-Id: <uuid> · {"status":"UP"}
```

Si cambió `SERVICE_HOST_PORT` en `.env`, use ese puerto. Para detener: `docker compose down` (agregue `-v` para borrar también el volumen de la base de datos).

## 3. Ejecutar sin Docker

Se necesita una base de datos PostgreSQL accesible con los datos de `.env`. Por ejemplo, una temporal:

```bash
cp .env.example .env
docker run -d --name documents-db-local \
  -e POSTGRES_DB=fleetops_documents -e POSTGRES_USER=documents_app -e POSTGRES_PASSWORD=change_me_db_password \
  -p 5432:5432 postgres:18.6-alpine3.24
./gradlew bootRun           # en Windows: gradlew.bat bootRun
```

Los valores `POSTGRES_*` deben coincidir con `DB_NAME`, `DB_USER` y `DB_PASSWORD` de su `.env`. La aplicación lee `.env` automáticamente (`spring.config.import: optional:file:.env[.properties]`).

## 4. Pruebas y cobertura

Esta versión **no incluye pruebas unitarias ni reporte de cobertura**. El único test, `FleetopsDocumentServiceApplicationTests`, verifica que el contexto de Spring arranque. Usa el perfil `test` (`src/test/resources/application-test.yaml`), así que no necesita `.env` ni una base de datos en ejecución:

```bash
./gradlew build             # compila, ejecuta el test y genera build/libs/app.jar
```

Las pruebas unitarias (JUnit + Mockito, AAA) y JaCoCo con 100 % sobre `domain` y `application` se agregarán en la siguiente versión (ver la sección 8).

## 5. Estructura del proyecto

```
├── build.gradle                 Build · Spring Boot 4.1.1, Java 21; genera build/libs/app.jar
├── Dockerfile                   Multi-stage (JDK → JRE alpine), usuario no-root, imágenes con versión fija
├── docker-compose.yml           PostgreSQL 18.6 (healthcheck, volumen) + servicio; red propia
├── .env.example                 Todas las variables documentadas
└── src/main/java/co/edu/univalle/fleetops/document/service/
    ├── domain/                              NÚCLEO · Dominio (Java puro)
    │   ├── model/                           Aggregate Document, Value Objects, tipos y estados (DAS 6.1, 6.2)
    │   ├── service/                         Regla única de estado por fecha (ADR-DOC-01), umbrales, cumplimiento
    │   ├── event/                           Eventos de dominio (DAS 8.1), sin document_number
    │   └── exception/                       ✔ BusinessRuleViolation (422), DomainConflict (409), ResourceNotFound (404)
    ├── application/                         NÚCLEO · Aplicación (sin Spring)
    │   ├── port/in/                         Casos de uso: registrar, corregir, anular, renovar, consultar…
    │   ├── port/out/                        ✔ BusinessClock · repositorios, outbox, auditoría, réplicas
    │   └── service/                         Implementación de los casos de uso
    └── infrastructure/                      ADAPTADORES
        ├── adapter/in/rest/                 ✔ CorrelationIdFilter (X-Correlation-Id), GlobalExceptionHandler (RFC 9457)
        ├── adapter/in/messaging/            Consumidor de réplicas · vacío hasta definir el broker (ADR-DOC-12)
        ├── adapter/in/scheduler/            Job de vigencia 06:00 UTC (HU-DOC-06/07)
        ├── adapter/out/persistence/         Adaptadores JPA (DAS 6.4 y 14)
        ├── adapter/out/messaging/           Publicador del outbox · vacío hasta definir el broker (ADR-DOC-12)
        ├── adapter/out/clock/               ✔ ZonedBusinessClock (America/Bogota)
        └── config/                          ✔ CoreConfiguration (Composition Root), DocumentServiceProperties
```

✔ = ya implementado. Cada paquete tiene un `package-info.java` que documenta su rol, los patrones que aplica, su trazabilidad al DAS y el contenido previsto.

**Regla de dependencia:** `infrastructure → application → domain`. El dominio no depende de nada.

### Lo que ya funciona

| Capacidad | Dónde | DAS |
| :-- | :-- | :-- |
| `GET /health`: 200 UP / 503 DOWN según la base de datos; el broker no se incluye | Actuator, `application.yaml` | HU-DOC-11, sec. 4.2 |
| Logs JSON (ECS) por salida estándar con `correlation_id` | `logging.structured` | sec. 10 |
| `X-Correlation-Id` en toda respuesta (se reutiliza si llega un UUID válido) | `CorrelationIdFilter` | sec. 4.2 y 7 |
| Errores en Problem Details con mensajes en español y `detalles` por campo; 503 si cae la BD | `GlobalExceptionHandler` | sec. 7 y 10 |
| Fecha de negocio en la zona horaria configurada | `ZonedBusinessClock` | sec. 5, ADR-DOC-01 |
| Umbrales de alerta validados al arrancar (si no son válidos, el servicio no inicia) | `DocumentServiceProperties` | ADR-DOC-09 |
| JSON en snake_case y marcas de tiempo en UTC | `spring.jackson` | sec. 6 y 7 |

## 6. Decisiones de arquitectura clave (resumen del DAS)

| ADR | Decisión |
| :-- | :-- |
| ADR-DOC-01 | El estado (VALID / EXPIRING_SOON / EXPIRED) se calcula solo por fecha y días restantes en America/Bogota; es una regla única compartida |
| ADR-DOC-04 / 11 | Renovación atómica; un solo documento activo por propietario y tipo (índice único parcial) |
| ADR-DOC-05 | Patrón Outbox: el evento se guarda en la misma transacción que el cambio |
| ADR-DOC-06 | Réplicas locales de vehículos y conductores con `is_active`; sin HTTP a otros servicios |
| ADR-DOC-07 | Una sola categoría por licencia |
| ADR-DOC-09 | Umbrales configurables por `ALERT_THRESHOLD_DAYS`, validados al arrancar |
| ADR-DOC-10 | Las reglas por tipo de documento viven en `document_types` (Type Object) |
| ADR-DOC-12 | El broker (Apache Kafka o AWS SNS + SQS) está por definir; mientras tanto, nada se publica |

**Convenciones agregadas por el arquetipo** (no definidas en el DAS):
- **Problem Details (RFC 9457):** es el formato de error estándar de la IETF y Spring lo soporta de forma nativa.
- **Wiring explícito en `infrastructure.config`:** mantiene el núcleo libre de Spring.
- **Sin Lombok:** se prefiere lo explícito; los objetos inmutables se modelan con `record`.

## 7. Configuración

Toda la configuración se hace con variables de entorno; vea [`.env.example`](.env.example), donde cada variable tiene su comentario. No hay credenciales en el repositorio.

## 8. Limitaciones conocidas y próximos pasos

**Limitaciones de esta versión**
- No hay migraciones (`ddl-auto: none`): la aplicación arranca y responde `/health`, pero todavía no tiene tablas ni endpoints de negocio.
- No hay pruebas unitarias ni JaCoCo; solo existe el test de arranque del contexto.
- No hay broker (ADR-DOC-12): no hay consumidor de réplicas ni publicador del outbox.
- La autenticación y los roles no están definidos (DAS sec. 12, punto 15).

**Puntos abiertos del DAS (sección 12)**
- #1: contenido de `document.renewed`.
- #6: documento obligatorio ausente / Asignaciones.
- #9: reemplazo de la tarjeta de propiedad.
- #11: PATCH sobre un documento vencido.
- #14: archivo digital.
- #15: roles y carga inicial.
- #16: elección del broker.

**Próximos pasos sugeridos** (orden del DAS sec. 13)
1. Migraciones Flyway con el MER (sec. 14) y el catálogo `document_types`.
2. Pruebas unitarias del dominio y la aplicación, con JaCoCo al 100 %.
3. HU-DOC-01/02 (registro) y HU-DOC-03 (consultas), escribiendo en el outbox y en la auditoría.
4. Datos semilla de réplicas para desarrollo; HU-DOC-10 cuando se defina el broker.
5. HU-DOC-06/07 (job), HU-DOC-05 (renovación), HU-DOC-04 (corrección y anulación), HU-DOC-08/09.
6. Pipeline CI/CD y OpenAPI en `fleetops-contracts` (HU-DOC-11).
