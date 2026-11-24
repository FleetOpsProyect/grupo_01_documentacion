package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Encuentra la restricción de la base de datos que provocó un error de integridad, para traducirlo a una respuesta
 * de negocio en lugar de un error genérico.
 */
final class ConstraintViolations {

	private ConstraintViolations() {
	}

	/**
	 * @return el nombre de la restricción violada, o {@code null} si el error no viene de una restricción
	 */
	static String constraintName(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation.getConstraintName();
			}
		}
		return null;
	}

}
