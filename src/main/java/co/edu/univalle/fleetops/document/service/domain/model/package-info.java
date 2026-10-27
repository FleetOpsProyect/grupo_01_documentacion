/**
 * Modelo de dominio.
 *
 * <p><b>Rol:</b> entidades, agregados y objetos de valor del negocio.
 *
 * <p><b>Patrones:</b> Aggregate Root ({@code Document}), Value Object ({@code ValidityPeriod},
 * {@code DocumentNumber}, {@code LicenseCategory}), Type Object para las reglas por tipo de documento
 * (ADR-DOC-10) y State para los estados del documento (DAS sec. 6.2).
 *
 * <p><b>Contenido previsto:</b>
 * <ul>
 *   <li>{@code Document}: agregado con comportamiento propio (registrar, corregir, renovar, anular) para
 *       evitar un modelo anémico.</li>
 *   <li>{@code DocumentTypeCode}: SOAT, TECHNICAL_INSPECTION, PROPERTY_CARD, DRIVER_LICENSE (DAS sec. 6.1).</li>
 *   <li>{@code OwnerType}: VEHICLE, DRIVER.</li>
 *   <li>{@code DocumentStatus}: VALID, EXPIRING_SOON, EXPIRED, REPLACED, CANCELLED; REPLACED y CANCELLED
 *       son finales.</li>
 *   <li>{@code DocumentTypeRules}: si vence, si es obligatorio, si se renueva y propietario permitido.</li>
 *   <li>{@code LicenseCategory}: una sola categoría por licencia (ADR-DOC-07).</li>
 * </ul>
 *
 * <p>Los nombres siguen el DAS: inglés en el código y snake_case en los datos ({@code expiration_date},
 * {@code owner_type}).
 */
package co.edu.univalle.fleetops.document.service.domain.model;
