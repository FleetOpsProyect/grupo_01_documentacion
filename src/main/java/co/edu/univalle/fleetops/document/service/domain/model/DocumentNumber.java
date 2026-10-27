package co.edu.univalle.fleetops.document.service.domain.model;

import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;

/**
 * Value Object: número del documento. Es único por tipo (DAS sec. 6.3) y, en las licencias, es un dato personal:
 * se enmascara en los logs dejando los últimos 4 caracteres y nunca viaja en los eventos (DAS sec. 4.2).
 *
 * @param value número sin espacios al inicio ni al final, de 1 a 50 caracteres
 */
public record DocumentNumber(String value) {

	static final int MAX_LENGTH = 50;

	private static final int VISIBLE_CHARS = 4;

	public DocumentNumber {
		if (value == null || value.isBlank()) {
			throw new BusinessRuleViolationException("document_number es obligatorio");
		}
		value = value.strip();
		if (value.length() > MAX_LENGTH) {
			throw new BusinessRuleViolationException(
					"document_number no puede tener más de " + MAX_LENGTH + " caracteres");
		}
	}

	/**
	 * Número enmascarado para logs: solo quedan visibles los últimos 4 caracteres. Si el número tiene 4 caracteres
	 * o menos, se enmascara completo.
	 */
	public String masked() {
		int hidden = Math.max(this.value.length() - VISIBLE_CHARS, 0);
		if (hidden == 0) {
			return "*".repeat(this.value.length());
		}
		return "*".repeat(hidden) + this.value.substring(hidden);
	}

	/**
	 * Devuelve el número enmascarado para que un log accidental no exponga el dato completo.
	 */
	@Override
	public String toString() {
		return masked();
	}

}
