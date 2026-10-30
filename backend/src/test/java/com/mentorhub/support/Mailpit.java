package com.mentorhub.support;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** A Mailpit SMTP server for tests, with access to the messages it received through its HTTP API. */
public final class Mailpit {

    /** A received message as Mailpit's HTTP API shows it. */
    public record Message(String from, String fromName, List<String> to, String subject, String text) {}

    private static final int SMTP_PORT = 1025;
    private static final int HTTP_PORT = 8025;

    private final GenericContainer<?> container;
    private RestClient api;

    public Mailpit() {
        // keep in sync with infra/compose.yaml
        container = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.4"))
                .withExposedPorts(SMTP_PORT, HTTP_PORT);
        api = RestClient.create();
    }

    public void start() {
        container.start();
        api = RestClient.create("http://" + container.getHost() + ":" + container.getMappedPort(HTTP_PORT));
    }

    public void stop() {
        container.stop();
    }

    public String host() {
        return container.getHost();
    }

    public int smtpPort() {
        return container.getMappedPort(SMTP_PORT);
    }

    public void deleteAll() {
        api.delete().uri("/api/v1/messages").retrieve().toBodilessEntity();
    }

    /** Waits up to five seconds for at least {@code count} messages; newest first. */
    public List<Message> awaitMessages(int count) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (true) {
            List<Map<String, Object>> summaries = summaries();
            if (summaries.size() >= count || Instant.now().isAfter(deadline)) {
                return summaries.stream().map(this::message).toList();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> summaries() {
        Map<String, Object> page = Objects.requireNonNull(api.get()
                .uri("/api/v1/messages")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
        return (List<Map<String, Object>>) page.get("messages");
    }

    @SuppressWarnings("unchecked")
    private Message message(Map<String, Object> summary) {
        Map<String, Object> message = Objects.requireNonNull(api.get()
                .uri("/api/v1/message/{id}", summary.get("ID"))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
        Map<String, Object> from = (Map<String, Object>) message.get("From");
        List<Map<String, Object>> to = (List<Map<String, Object>>) message.get("To");
        return new Message(
                (String) from.get("Address"),
                (String) from.get("Name"),
                to.stream().map(address -> (String) address.get("Address")).toList(),
                (String) message.get("Subject"),
                (String) message.get("Text"));
    }
}
