package com.example.consumer;

import com.example.container.KafkaRaftWithExtraListenersContainer;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;

import java.util.List;

public class ConsumerApplicationTests {

    public static void main(String[] args) {
        SpringApplication.from(ConsumerApplication::main)
                .with(ContainerConfiguration.class)
                .run(args);
    }

    @TestConfiguration
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
        KafkaContainer kafkaContainer() {
            return new KafkaRaftWithExtraListenersContainer("confluentinc/cp-kafka:7.4.0")
                    .withAdditionalListener(() -> "kafka:19092")
                    .withKraft()
                    .withNetwork(network)
                    .withNetworkAliases("kafka")
                    .withReuse(true);
        }

    }

}
