package co.edu.univalle.fleetops.document.service.infrastructure.config;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MessagingPropertiesTest {

	private static final MessagingProperties.Consumer CONSUMER = new MessagingProperties.Consumer(20, 10,
			Duration.ofSeconds(5));

	private static final MessagingProperties.Queues QUEUES = new MessagingProperties.Queues(
			"fleetops_document_driver_events_queue", "fleetops_document_vehicle_events_queue");

	private static final String TOPIC_ARN = "arn:aws:sns:us-east-2:123456789012:fleetops_document_events";

	private static final Duration TWO_SECONDS = Duration.ofSeconds(2);

	private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

	private static final MessagingProperties.Publisher PUBLISHER = new MessagingProperties.Publisher(true, TOPIC_ARN,
			50, TWO_SECONDS, TEN_SECONDS);

	@Test
	void should_accept_the_local_profile_with_endpoint_and_static_credentials() {
		MessagingProperties properties = new MessagingProperties(true, "us-east-2", "http://localhost:4566", "test",
				"test", CONSUMER, QUEUES, PUBLISHER);

		assertThat(properties.endpointUri()).isEqualTo(URI.create("http://localhost:4566"));
		assertThat(properties.hasStaticCredentials()).isTrue();
	}

	@Test
	void should_treat_blank_values_of_the_aws_profile_as_absent() {
		MessagingProperties properties = new MessagingProperties(true, " us-east-2 ", "", " ", null, CONSUMER,
				QUEUES, PUBLISHER);

		assertThat(properties.region()).isEqualTo("us-east-2");
		assertThat(properties.endpointUri()).isNull();
		assertThat(properties.hasStaticCredentials()).isFalse();
	}

	@Test
	void should_require_the_region() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties(true, " ", null, null, null, CONSUMER, QUEUES, PUBLISHER))
			.withMessageContaining("AWS_REGION");
	}

	@Test
	void should_reject_a_relative_endpoint() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties(true, "us-east-2", "localhost:4566", null, null, CONSUMER,
					QUEUES, PUBLISHER))
			.withMessageContaining("MESSAGING_ENDPOINT");
	}

	@Test
	void should_require_both_static_credentials_or_none() {
		assertThatIllegalArgumentException()
			.isThrownBy(
					() -> new MessagingProperties(true, "us-east-2", null, "test", null, CONSUMER, QUEUES, PUBLISHER));
		assertThatIllegalArgumentException()
			.isThrownBy(
					() -> new MessagingProperties(true, "us-east-2", null, null, "test", CONSUMER, QUEUES, PUBLISHER));
	}

	@Test
	void should_require_consumer_and_queues() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties(true, "us-east-2", null, null, null, null, QUEUES, PUBLISHER));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties(true, "us-east-2", null, null, null, CONSUMER, null, PUBLISHER));
	}

	@Test
	void should_validate_the_consumer_settings() {
		Duration delay = Duration.ofSeconds(5);
		assertThatIllegalArgumentException().isThrownBy(() -> new MessagingProperties.Consumer(-1, 10, delay));
		assertThatIllegalArgumentException().isThrownBy(() -> new MessagingProperties.Consumer(21, 10, delay));
		assertThatIllegalArgumentException().isThrownBy(() -> new MessagingProperties.Consumer(20, 0, delay));
		assertThatIllegalArgumentException().isThrownBy(() -> new MessagingProperties.Consumer(20, 11, delay));
		assertThatIllegalArgumentException().isThrownBy(() -> new MessagingProperties.Consumer(20, 10, null));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties.Consumer(20, 10, Duration.ZERO));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties.Consumer(20, 10, Duration.ofSeconds(-1)));
		assertThat(new MessagingProperties.Consumer(0, 1, Duration.ofMillis(1)).waitTimeSeconds()).isZero();
	}

	@Test
	void should_require_the_queue_names() {
		assertThatIllegalArgumentException().isThrownBy(() -> new MessagingProperties.Queues(null, "q"));
		assertThatIllegalArgumentException().isThrownBy(() -> new MessagingProperties.Queues(" ", "q"));
		assertThatIllegalArgumentException().isThrownBy(() -> new MessagingProperties.Queues("q", null));
		assertThatIllegalArgumentException().isThrownBy(() -> new MessagingProperties.Queues("q", ""));
	}

	@Test
	void should_require_the_publisher() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties(true, "us-east-2", null, null, null, CONSUMER, QUEUES, null))
			.withMessageContaining("publisher");
	}

	@Test
	void should_accept_the_publisher_settings() {
		MessagingProperties.Publisher publisher = new MessagingProperties.Publisher(false, " " + TOPIC_ARN + " ", 1,
				TWO_SECONDS, TEN_SECONDS);

		assertThat(publisher.topicArn()).isEqualTo(TOPIC_ARN);
		assertThat(publisher.enabled()).isFalse();
		assertThat(new MessagingProperties.Publisher(true, "arn:aws-us-gov:sns:us-gov-west-1:123456789012:t", 500,
				TWO_SECONDS, TEN_SECONDS).batchSize()).isEqualTo(500);
	}

	@Test
	void should_reject_an_invalid_topic_arn() {
		for (String arn : new String[] { null, " ", "fleetops_document_events",
				"arn:aws:sqs:us-east-2:123456789012:fleetops_document_events",
				"arn:aws:sns:us-east-2:12345:fleetops_document_events" }) {
			assertThatIllegalArgumentException()
				.isThrownBy(() -> new MessagingProperties.Publisher(true, arn, 50, TWO_SECONDS, TEN_SECONDS))
				.withMessageContaining("MESSAGING_DOCUMENT_TOPIC_ARN");
		}
	}

	@Test
	void should_validate_the_publisher_settings() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties.Publisher(true, TOPIC_ARN, 0, TWO_SECONDS, TEN_SECONDS));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties.Publisher(true, TOPIC_ARN, 501, TWO_SECONDS, TEN_SECONDS));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties.Publisher(true, TOPIC_ARN, 50, null, TEN_SECONDS));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties.Publisher(true, TOPIC_ARN, 50, Duration.ZERO, TEN_SECONDS));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MessagingProperties.Publisher(true, TOPIC_ARN, 50, TWO_SECONDS, null));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new MessagingProperties.Publisher(true, TOPIC_ARN, 50, TWO_SECONDS, Duration.ofSeconds(-1)));
	}

}
