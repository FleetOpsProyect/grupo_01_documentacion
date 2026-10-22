package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import co.edu.univalle.fleetops.document.service.domain.exception.BusinessRuleViolationException;
import co.edu.univalle.fleetops.document.service.domain.exception.DomainConflictException;
import co.edu.univalle.fleetops.document.service.domain.exception.ResourceNotFoundException;

/**
 * Traduce las excepciones a Problem Details (RFC 9457) siguiendo las convenciones del DAS sec. 7: 400 para
 * solicitudes mal formadas o parámetros inválidos, 404 para recursos inexistentes, 409 para conflictos y 422 para
 * validaciones de negocio, con mensajes en español y el detalle por campo en {@code detalles}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final String DETAILS_PROPERTY = "detalles";

	@ExceptionHandler(BusinessRuleViolationException.class)
	ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Regla de negocio no cumplida", ex.getMessage());
	}

	@ExceptionHandler(DomainConflictException.class)
	ProblemDetail handleConflict(DomainConflictException ex) {
		return problem(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
	}

	/**
	 * Base de datos caída: respuesta controlada 503 (DAS sec. 10, resiliencia).
	 */
	@ExceptionHandler({ CannotCreateTransactionException.class, DataAccessResourceFailureException.class })
	ProblemDetail handleDatabaseUnavailable(Exception ex) {
		log.error("Base de datos no disponible", ex);
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
				"El servicio no puede atender la solicitud en este momento. Intente de nuevo más tarde.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Error no controlado", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
				"Ocurrió un error inesperado. Use el X-Correlation-Id para rastrearlo.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> details = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> Map.of("campo", error.getField(), "mensaje", String.valueOf(error.getDefaultMessage())))
			.toList();
		ProblemDetail body = problem(HttpStatus.UNPROCESSABLE_CONTENT, "Datos inválidos",
				"La solicitud tiene campos que no cumplen las validaciones.");
		body.setProperty(DETAILS_PROPERTY, details);
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).headers(headers).body(body);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Solicitud mal formada",
				"El cuerpo de la solicitud no es un JSON válido o tiene tipos de dato incorrectos.");
		return ResponseEntity.badRequest().headers(headers).body(body);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Parámetro inválido",
				"El parámetro '" + ex.getPropertyName() + "' tiene un formato inválido.");
		return ResponseEntity.badRequest().headers(headers).body(body);
	}

	@Override
	protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail body = problem(HttpStatus.NOT_FOUND, "Recurso no encontrado",
				"La ruta '/" + ex.getResourcePath() + "' no existe.");
		return ResponseEntity.status(HttpStatus.NOT_FOUND).headers(headers).body(body);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
