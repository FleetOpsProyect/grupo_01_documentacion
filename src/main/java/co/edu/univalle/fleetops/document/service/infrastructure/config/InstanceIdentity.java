package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Identifica la instancia del servicio en scheduled_job_runs.instance_id (máximo 100 caracteres): nombre del host
 * (en Docker, el id del contenedor) y PID del proceso.
 */
public final class InstanceIdentity {

	static final int MAX_LENGTH = 100;

	static final String UNKNOWN_HOST = "unknown-host";

	private InstanceIdentity() {
	}

	public static String current() {
		return of(hostName(() -> InetAddress.getLocalHost().getHostName()), ProcessHandle.current().pid());
	}

	static String of(String hostName, long pid) {
		String host = (hostName == null || hostName.isBlank()) ? UNKNOWN_HOST : hostName.strip();
		String suffix = "/" + pid;
		int maxHost = MAX_LENGTH - suffix.length();
		return (host.length() > maxHost ? host.substring(0, maxHost) : host) + suffix;
	}

	/**
	 * Un host sin nombre resoluble no impide arrancar: la instancia se identifica igual por su PID.
	 */
	static String hostName(HostNameResolver resolver) {
		try {
			return resolver.resolve();
		}
		catch (UnknownHostException ex) {
			return UNKNOWN_HOST;
		}
	}

	/**
	 * Obtiene el nombre del host; permite probar el caso en que no se puede resolver.
	 */
	@FunctionalInterface
	interface HostNameResolver {

		String resolve() throws UnknownHostException;

	}

}
