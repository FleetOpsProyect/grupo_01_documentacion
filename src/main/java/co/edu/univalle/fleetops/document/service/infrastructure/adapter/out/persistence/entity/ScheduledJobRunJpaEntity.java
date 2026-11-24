package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla {@code document.scheduled_job_runs}: una ejecución por job y fecha de negocio (ADR-DOC-02), con el resumen
 * de la verificación (HU-DOC-06, criterio 7).
 */
@Entity
@Table(name = "scheduled_job_runs")
public class ScheduledJobRunJpaEntity {

	/**
	 * Estado de una ejecución en curso.
	 */
	public static final String RUNNING = "RUNNING";

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "job_name", nullable = false, length = 60)
	private String jobName;

	@Column(name = "business_date", nullable = false)
	private LocalDate businessDate;

	@Column(name = "status", nullable = false, length = 15)
	private String status;

	@Column(name = "instance_id", nullable = false, length = 100)
	private String instanceId;

	@Column(name = "evaluated_count", nullable = false)
	private int evaluatedCount;

	@Column(name = "issued_count", nullable = false)
	private int issuedCount;

	@Column(name = "failed_count", nullable = false)
	private int failedCount;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	protected ScheduledJobRunJpaEntity() {
	}

	/**
	 * Ejecución nueva en estado RUNNING.
	 */
	public ScheduledJobRunJpaEntity(UUID id, String jobName, LocalDate businessDate, String instanceId,
			Instant startedAt) {
		this.id = id;
		this.jobName = jobName;
		this.businessDate = businessDate;
		this.status = RUNNING;
		this.instanceId = instanceId;
		this.startedAt = startedAt;
	}

	/**
	 * Cierra la ejecución con su estado final y su resumen.
	 */
	public void finish(String finalStatus, int evaluated, int issued, int failed, Instant finishedAtValue) {
		this.status = finalStatus;
		this.evaluatedCount = evaluated;
		this.issuedCount = issued;
		this.failedCount = failed;
		this.finishedAt = finishedAtValue;
	}

	public UUID getId() {
		return this.id;
	}

	public String getJobName() {
		return this.jobName;
	}

	public LocalDate getBusinessDate() {
		return this.businessDate;
	}

	public String getStatus() {
		return this.status;
	}

	public String getInstanceId() {
		return this.instanceId;
	}

	public int getEvaluatedCount() {
		return this.evaluatedCount;
	}

	public int getIssuedCount() {
		return this.issuedCount;
	}

	public int getFailedCount() {
		return this.failedCount;
	}

	public Instant getStartedAt() {
		return this.startedAt;
	}

	public Instant getFinishedAt() {
		return this.finishedAt;
	}

}
