/**
 * Adaptador de salida de persistencia: PostgreSQL con JPA (DAS sec. 6.4 y 14).
 *
 * <p><b>Rol:</b> implementa los puertos de salida de repositorio, outbox y auditoría.
 *
 * <p><b>Patrones:</b> Adapter, Repository y Mapper dominio-JPA. Las entidades JPA están separadas de las
 * entidades de dominio.
 *
 * <p><b>Estado:</b> implementado para HU-DOC-01 y HU-DOC-02: documentos, catálogo de tipos, réplicas (solo
 * lectura), outbox y auditoría. El esquema lo crean los scripts de {@code database/} al iniciar PostgreSQL; la
 * aplicación no lo modifica y al arrancar valida que las entidades coincidan con él ({@code ddl-auto: validate}).
 *
 * <p><b>Notas para la implementación:</b>
 * <ul>
 *   <li>Traducir las violaciones de restricciones únicas a excepciones de conflicto (409), incluida la
 *       restricción única parcial de ADR-DOC-11.</li>
 *   <li>En la renovación, forzar el flush del documento anterior a REPLACED antes de insertar el nuevo:
 *       Hibernate ejecuta los INSERT antes que los UPDATE.</li>
 *   <li>Los filtros por estado se calculan sobre {@code expiration_date} y la fecha de negocio, no sobre la
 *       columna {@code status} (ADR-DOC-01).</li>
 * </ul>
 */
package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;
