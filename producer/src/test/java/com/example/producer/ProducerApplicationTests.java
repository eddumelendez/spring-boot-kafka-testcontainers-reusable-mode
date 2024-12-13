package com.example.producer;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ProducerApplicationTests {

    public static void main(String[] args) {
        SpringApplication.from(ProducerApplication::main)
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
        KafkaContainer kafkaContainer() {
            return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
                    .withListener(() -> "kafka:19092")
                    .withEmbeddedZookeeper()
                    .withNetwork(network)
                    .withReuse(true);
        }

//        @Bean
//        @DependsOn("kafkaContainer")
        GenericContainer<?> controlCenter() {
            GenericContainer<?> schemaRegistry = new GenericContainer<>("confluentinc/cp-schema-registry:7.4.0")
                    .withExposedPorts(8085)
                    .withNetworkAliases("schemaregistry")
                    .withNetwork(network)
                    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
                    .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8085")
                    .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schemaregistry")
                    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL", "PLAINTEXT")
                    .waitingFor(Wait.forHttp("/subjects"))
                    .withStartupTimeout(Duration.of(120, ChronoUnit.SECONDS));

            GenericContainer<?> ksqldb = new GenericContainer<>("confluentinc/cp-ksqldb-server:7.4.0")
                    .withExposedPorts(8088)
                    .withNetwork(network)
                    .withNetworkAliases("ksqldb")
                    .withEnv("KSQL_LISTENERS", "http://0.0.0.0:8088")
                    .withEnv("KSQL_KSQL_SERVICE_ID", "ksqldb-server")
                    .withEnv("KSQL_BOOTSTRAP_SERVERS", "kafka:19092")
                    .withEnv("KSQL_KSQL_SCHEMA_REGISTRY_URL", "http://schemaregistry:8085");

            GenericContainer<?> connect = new GenericContainer("confluentinc/cp-server-connect:7.4.0") {
                @Override
                protected void containerIsStarting(InspectContainerResponse containerInfo) {
                    try {
                        execInContainer("confluent-hub", "install", "--no-prompt", "confluentinc/kafka-connect-s3:latest");
                        execInContainer("confluent-hub", "install", "--no-prompt", "confluentinc/kafka-connect-jdbc:latest");
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException("Error downloading connectors", e);
                    }

                }
            }
                    .withExposedPorts(8083)
                    .withNetwork(network)
                    .withNetworkAliases("connect")
                    .waitingFor(Wait.forHttp("/connectors").forPort(8083))
                    .withEnv("CONNECT_BOOTSTRAP_SERVERS", "kafka:19092")
                    .withEnv("CONNECT_LISTENERS", "http://0.0.0.0:8083")
                    .withEnv("CONNECT_GROUP_ID", "connect-cluster")
                    .withEnv("CONNECT_CONFIG_STORAGE_TOPIC", "connect-configs")
                    .withEnv("CONNECT_OFFSET_STORAGE_TOPIC", "connect-offsets")
                    .withEnv("CONNECT_STATUS_STORAGE_TOPIC", "connect-statuses")
                    .withEnv("CONNECT_KEY_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
                    .withEnv("CONNECT_VALUE_CONVERTER", "org.apache.kafka.connect.json.JsonConverter")
                    .withEnv("CONNECT_REST_ADVERTISED_HOST_NAME", "connect")
                    .withEnv("CONNECT_REST_ADVERTISED_PORT", "8083")
                    .withEnv("CONNECT_PLUGIN_PATH", "/usr/share/java,/usr/share/confluent-hub-components")
                    .withEnv("CONNECT_REPLICATION_FACTOR", "1")
                    .withEnv("CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR", "1")
                    .withEnv("CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR", "1")
                    .withEnv("CONNECT_STATUS_STORAGE_REPLICATION_FACTOR", "1")
                    .withEnv("CONNECT_PRODUCER_CLIENT_ID", "connect-worker-producer");

            GenericContainer<?> restProxy = new GenericContainer<>("confluentinc/cp-kafka-rest:7.4.0")
                    .withExposedPorts(8082)
                    .withNetwork(network)
                    .withEnv("KAFKA_REST_HOST_NAME", "rest-proxy")
                    .withEnv("KAFKA_REST_LISTENERS", "http://0.0.0.0:8082")
                    .withEnv("KAFKA_REST_BOOTSTRAP_SERVERS", "kafka:19092")
                    .withEnv("KAFKA_REST_SCHEMA_REGISTRY_URL", "http://schemaregistry:8085")
                    .withLabel("com.testcontainers.desktop.service", "restproxy");

            Startables.deepStart(schemaRegistry, ksqldb, connect, restProxy).join();

            return new GenericContainer<>("confluentinc/cp-enterprise-control-center:7.4.0")
                    .withExposedPorts(9021, 9022)
                    .withNetwork(network)
                    .withEnv("CONTROL_CENTER_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
                    .withEnv("CONTROL_CENTER_REPLICATION_FACTOR", "1")
                    .withEnv("CONTROL_CENTER_INTERNAL_TOPICS_PARTITIONS", "1")
                    .withEnv("CONTROL_CENTER_SCHEMA_REGISTRY_SR1_URL", "http://schemaregistry:8085")
                    .withEnv("CONTROL_CENTER_SCHEMA_REGISTRY_URL", "http://schemaregistry:8085")
                    .withEnv("CONTROL_CENTER_KSQL_KSQLDB1_URL", "http://ksqldb:8088")
                    .withEnv("CONTROL_CENTER_KSQL_KSQLDB1_ADVERTISED_URL", "http://ksqldb:8088")
                    .withEnv("CONTROL_CENTER_CONNECT_CONNECT1_CLUSTER", "http://connect:8083")
                    .waitingFor(Wait.forHttp("/clusters").forPort(9021).allowInsecure())
                    .withStartupTimeout(Duration.of(120, ChronoUnit.SECONDS))
                    .withLabel("com.testcontainers.desktop.service", "cp-control-center");
        }

    }

}
