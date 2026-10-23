package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestUsers;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Spec invitation-emails, AC-8 and AC-11 against a mail server that accepts connections and never answers: the request
 * returns at once, and the email fails after the mail timeouts.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class InvitationEmailMailServerTimeoutTests {

    private static final HangingServer server = new HangingServer();

    @DynamicPropertySource
    static void mailServer(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", server::port);
        registry.add("spring.mail.properties.mail.smtp.connectiontimeout", () -> "1000");
        registry.add("spring.mail.properties.mail.smtp.timeout", () -> "1000");
        registry.add("spring.mail.properties.mail.smtp.writetimeout", () -> "1000");
        registry.add("mentorhub.mail.from", () -> "no-reply@mentorhub.test");
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @Autowired
    InvitationAdministrationService service;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theRequestDoesNotWaitForAHungMailServer(CapturedOutput output) throws Exception {
        TestUsers users = new TestUsers(jdbc);
        users.deleteAll();
        jdbc.sql("delete from event_publication").update();
        UUID admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        SecurityContextHolder.getContext()
                .setAuthentication(TestAuthentications.activeUserAuthentication(admin, Permission.USERS_INVITE));

        Instant start = Instant.now();
        UUID invitation =
                service.create("ivan@x.test", Set.of(), true).invitation().id();
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(elapsed).isLessThan(Duration.ofMillis(800));
        assertThat(server.connected.await(5, TimeUnit.SECONDS)).isTrue();
        Instant deadline = Instant.now().plusSeconds(10);
        while (!"FAILED".equals(status(invitation)) && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertThat(status(invitation)).isEqualTo("FAILED");
        assertThat(jdbc.sql("select failure from invitation_emails")
                        .query(String.class)
                        .single())
                .isNotBlank();
        assertThat(output.getAll()).contains("Invitation email").contains("failed");
    }

    private String status(UUID invitation) {
        return jdbc.sql("select status from invitation_emails where invitation_id = :id")
                .param("id", invitation)
                .query(String.class)
                .single();
    }

    /** Accepts SMTP connections and never sends the greeting. */
    private static final class HangingServer {

        final CountDownLatch connected = new CountDownLatch(1);
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final ServerSocket socket;

        HangingServer() {
            try {
                socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            Thread acceptor = new Thread(() -> {
                while (!socket.isClosed()) {
                    try {
                        sockets.add(socket.accept());
                        connected.countDown();
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        void close() {
            try {
                socket.close();
                for (Socket accepted : sockets) {
                    accepted.close();
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
