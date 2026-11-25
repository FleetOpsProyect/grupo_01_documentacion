package co.edu.univalle.fleetops.document.service.infrastructure.adapter.out.messaging;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

import co.edu.univalle.fleetops.document.service.application.port.out.EventPublicationException;
import co.edu.univalle.fleetops.document.service.application.port.out.OutboxRepository.PendingEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SnsEventPublisherTest {

	private static final String TOPIC = "fleetops_document_events";

	private static final String TOPIC_ARN = "arn:aws:sns:us-east-2:123456789012:" + TOPIC;

	private static final PendingEvent EVENT = new PendingEvent(
			UUID.fromString("019aaa00-0000-7000-8000-0000000000e1"), "document_registered_event", TOPIC,
			"{\"event_id\": \"019aaa00-0000-7000-8000-0000000000e1\"}", null);

	@Mock
	private SnsClient sns;

	private SnsEventPublisher publisher() {
		return new SnsEventPublisher(this.sns, TOPIC_ARN);
	}

	private static SnsException snsError(String code, int status) {
		return (SnsException) SnsException.builder()
			.statusCode(status)
			.message(code)
			.awsErrorDetails(AwsErrorDetails.builder().errorCode(code).errorMessage(code).build())
			.build();
	}

	@Test
	void should_publish_the_envelope_with_the_event_type_attribute() {
		// Act
		publisher().publish(EVENT);

		// Assert (ADR-DOC-14): sobre como Message y event_type como Message Attribute String
		ArgumentCaptor<PublishRequest> request = ArgumentCaptor.forClass(PublishRequest.class);
		verify(this.sns).publish(request.capture());
		assertThat(request.getValue().topicArn()).isEqualTo(TOPIC_ARN);
		assertThat(request.getValue().message()).isEqualTo(EVENT.payload());
		assertThat(request.getValue().messageAttributes()).containsOnlyKeys("event_type");
		assertThat(request.getValue().messageAttributes().get("event_type").dataType()).isEqualTo("String");
		assertThat(request.getValue().messageAttributes().get("event_type").stringValue())
			.isEqualTo("document_registered_event");
	}

	@ParameterizedTest
	@ValueSource(strings = { "InvalidParameter", "InvalidParameterValue", "ValidationException" })
	void should_classify_a_rejected_event_as_permanent(String code) {
		given(this.sns.publish(any(PublishRequest.class))).willThrow(snsError(code, 400));

		assertThatExceptionOfType(EventPublicationException.class).isThrownBy(() -> publisher().publish(EVENT))
			.satisfies(ex -> assertThat(ex.retryable()).isFalse())
			.withMessageContaining(code);
	}

	@ParameterizedTest
	@ValueSource(strings = { "AuthorizationError", "NotFound", "Throttled", "InternalError", "KMSAccessDenied" })
	void should_retry_configuration_and_service_errors(String code) {
		// D-2: un error que afecta a todos los eventos no los pasa a FAILED
		given(this.sns.publish(any(PublishRequest.class))).willThrow(snsError(code, 403));

		assertThatExceptionOfType(EventPublicationException.class).isThrownBy(() -> publisher().publish(EVENT))
			.satisfies(ex -> assertThat(ex.retryable()).isTrue());
	}

	@Test
	void should_retry_a_service_error_without_details() {
		given(this.sns.publish(any(PublishRequest.class)))
			.willThrow(SnsException.builder().statusCode(500).message("sin detalles").build());

		assertThatExceptionOfType(EventPublicationException.class).isThrownBy(() -> publisher().publish(EVENT))
			.satisfies(ex -> assertThat(ex.retryable()).isTrue());
	}

	@Test
	void should_retry_when_sns_is_unreachable() {
		given(this.sns.publish(any(PublishRequest.class)))
			.willThrow(SdkClientException.create("Unable to execute HTTP request"));

		assertThatExceptionOfType(EventPublicationException.class).isThrownBy(() -> publisher().publish(EVENT))
			.satisfies(ex -> assertThat(ex.retryable()).isTrue())
			.withMessageContaining("SNS no está disponible");
	}

	@Test
	void should_retry_an_event_whose_topic_has_no_configured_arn() {
		PendingEvent otherTopic = new PendingEvent(EVENT.eventId(), EVENT.eventType(), "otro_topico", "{}", null);

		assertThatExceptionOfType(EventPublicationException.class)
			.isThrownBy(() -> publisher().publish(otherTopic))
			.satisfies(ex -> assertThat(ex.retryable()).isTrue())
			.withMessageContaining("otro_topico");
		verifyNoInteractions(this.sns);
	}

	@Test
	void should_reject_an_event_larger_than_the_sns_limit_without_calling_it() {
		PendingEvent huge = new PendingEvent(EVENT.eventId(), EVENT.eventType(), TOPIC,
				"x".repeat(SnsEventPublisher.MAX_MESSAGE_BYTES), null);

		assertThatExceptionOfType(EventPublicationException.class).isThrownBy(() -> publisher().publish(huge))
			.satisfies(ex -> assertThat(ex.retryable()).isFalse());
		verifyNoInteractions(this.sns);
	}

	@Test
	void should_reject_missing_dependencies() {
		assertThatNullPointerException().isThrownBy(() -> new SnsEventPublisher(null, TOPIC_ARN)).withMessage("sns");
		assertThatNullPointerException().isThrownBy(() -> new SnsEventPublisher(this.sns, null))
			.withMessage("topicArn");
	}

}
