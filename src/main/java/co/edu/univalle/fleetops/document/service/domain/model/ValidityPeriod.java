package co.edu.univalle.fleetops.document.service.domain.model;

import java.time.LocalDate;

import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;

/**
 * Value Object: vigencia de un documento (DAS sec. 6.3). Si hay fecha de vencimiento, debe ser posterior a la de
 * inicio. Que sea obligatoria o no depende del tipo de documento ({@link DocumentTypeRules#hasExpiration()}).
 *
 * @param issueDate fecha de inicio de vigencia
 * @param expirationDate fecha de vencimiento; vacía en los tipos que no vencen
 */
public record ValidityPeriod(LocalDate issueDate, LocalDate expirationDate) {

	public ValidityPeriod {
		if (issueDate == null) {
			throw new BusinessRuleViolationException("issue_date es obligatoria");
		}
		if (expirationDate != null && !expirationDate.isAfter(issueDate)) {
			throw new BusinessRuleViolationException("expiration_date debe ser posterior a issue_date");
		}
	}

	public boolean hasExpiration() {
		return this.expirationDate != null;
	}

}
