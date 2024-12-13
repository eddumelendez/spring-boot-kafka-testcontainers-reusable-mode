package com.example.consumer;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.util.List;

public class ConsumerApplicationTests {

    public static void main(String[] args) {
        SpringApplication.from(ConsumerApplication::main)
                .with(ContainerConfiguration.class)
                .run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {

        private static final String KAFKA_NETWORK = "kafka-network";

        Network network = getNetwork();

        static Network getNetwork() {
            Network defaultDaprNetwork = new Network() {
                @Override
                public String getId() {
                    return KAFKA_NETWORK;
                }

                @Override
                public void close() {

                }

                @Override
                public Statement apply(Statement base, Description description) {
                    return null;
                }
            };

            List<com.github.dockerjava.api.model.Network> networks = DockerClientFactory.instance().client().listNetworksCmd().withNameFilter(KAFKA_NETWORK).exec();
            if (networks.isEmpty()) {
                Network.builder()
                        .createNetworkCmdModifier(cmd -> cmd.withName(KAFKA_NETWORK))
                        .build().getId();
                return defaultDaprNetwork;
            } else {
                return defaultDaprNetwork;
            }
        }

        @Bean
        @ServiceConnection
        @RestartScope
        ConfluentKafkaContainer kafkaContainer() {
            return new ConfluentKafkaContainer("confluentinc/cp-kafka:7.4.0")
                    .withListener("kafka:19092")
                    .withNetwork(network)
                    .withReuse(true);
        }

    }

}
