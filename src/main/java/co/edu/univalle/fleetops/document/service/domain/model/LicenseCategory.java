package co.edu.univalle.fleetops.document.service.domain.model;

import java.util.regex.Pattern;

import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;

/**
 * Value Object: única categoría de una licencia de conducción (ADR-DOC-07). Si el conductor tiene varias, se
 * registra la más específica según la política de la empresa.
 *
 * @param value una letra mayúscula seguida de un dígito opcional (A1, B2, C3...), igual que la restricción
 * {@code ck_documents_license_category} del DDL
 */
public record LicenseCategory(String value) {

	private static final Pattern FORMAT = Pattern.compile("^[A-Z][0-9]?$");

	public LicenseCategory {
		if (value == null || value.isBlank()) {
			throw new BusinessRuleViolationException("license_category es obligatoria");
		}
		value = value.strip();
		if (!FORMAT.matcher(value).matches()) {
			throw new BusinessRuleViolationException(
					"license_category debe ser una sola categoría: una letra mayúscula y un dígito opcional (por ejemplo C1)");
		}
	}

}
