package co.edu.univalle.fleetops.document.service.infrastructure.adapter.in.rest;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlation Identifier: toda respuesta incluye {@code X-Correlation-Id} (DAS sec. 4.2 y 7) y todo log de la
 * petición lleva {@code correlation_id} (DAS sec. 10).
 *
 * <p>Se reutiliza el id recibido si es un UUID válido; si no, se genera uno nuevo.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Correlation-Id";

	public static final String MDC_KEY = "correlation_id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));
		MDC.put(MDC_KEY, correlationId);
		response.setHeader(HEADER_NAME, correlationId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	private static String resolveCorrelationId(String received) {
		if (received != null) {
			try {
				return UUID.fromString(received.trim()).toString();
			}
			catch (IllegalArgumentException ignored) {
				// Un id que no es UUID se reemplaza por uno nuevo
			}
		}
		return UUID.randomUUID().toString();
	}

}
