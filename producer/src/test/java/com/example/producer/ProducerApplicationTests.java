package com.example.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class ProducerApplicationTests {

	public static void main(String[] args) {
		SpringApplication.from(ProducerApplication::main)
				.with(ContainerConfiguration.class)
				.run(args);
	}

	@TestConfiguration
	static class ContainerConfiguration {

		@Bean
		@ServiceConnection
		@RestartScope
		KafkaContainer kafkaContainer() {
			return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"))
					.withReuse(true);
		}

	}

}
