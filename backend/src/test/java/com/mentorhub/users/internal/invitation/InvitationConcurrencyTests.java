package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.AccessStatus;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * AC-15: parallel operations on one invitation or one email are serialized; the loser gets its documented error,
 * never a deadlock or another exception.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InvitationConcurrencyTests {

    private static final String OK = "OK";
    private static final String INVALID = InvitationProblems.INVITATION_INVALID.toString();
    private static final String CLOSED = InvitationProblems.INVITATION_CLOSED.toString();
    private static final String EXISTS = InvitationProblems.INVITATION_EXISTS.toString();

    @Autowired
    InvitationAdministrationService administration;

    @Autowired
    InvitationAcceptanceService acceptance;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;
    UUID admin;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
    }

    // Repeated because a race shows only in some runs.
    @RepeatedTest(10)
    void parallelAcceptsOfOneTokenChangeOnce() throws Exception {
        IssuedInvitation invited = invite("ivan@x.test");
        String token = token(invited);

        List<String> outcomes = inParallel(
                as(caller("subject-a"), () -> acceptance.accept(token)),
                as(caller("subject-a"), () -> acceptance.accept(token)));

        assertThat(outcomes).containsExactly(OK, OK);
        assertChangedOnce();
    }

    // Two identities with the same verified email: the second finds the invitation accepted by someone else.
    @RepeatedTest(10)
    void parallelAcceptsByDifferentIdentitiesLetOneWin() throws Exception {
        String token = token(invite("ivan@x.test"));

        List<String> outcomes = inParallel(
                as(caller("subject-a"), () -> acceptance.accept(token)),
                as(caller("subject-b"), () -> acceptance.accept(token)));

        assertThat(outcomes).containsExactlyInAnyOrder(OK, INVALID);
        assertChangedOnce();
    }

    @RepeatedTest(10)
    void acceptAndRevokeInParallelLetOneWin() throws Exception {
        IssuedInvitation invited = invite("ivan@x.test");
        String token = token(invited);

        List<String> outcomes = inParallel(
                as(caller("subject-a"), () -> acceptance.accept(token)),
                as(
                        adminAuthentication(),
                        () -> administration.revoke(invited.invitation().id())));

        assertThat(outcomes).isIn(List.of(OK, CLOSED), List.of(INVALID, OK));
        assertThat(userAuditEntries()).isEqualTo(OK.equals(outcomes.getFirst()) ? 1 : 0);
    }

    @RepeatedTest(10)
    void acceptAndNewLinkInParallelLetOneWin() throws Exception {
        IssuedInvitation invited = invite("ivan@x.test");
        String token = token(invited);

        List<String> outcomes = inParallel(
                as(caller("subject-a"), () -> acceptance.accept(token)),
                as(
                        adminAuthentication(),
                        () -> administration.reissueLink(invited.invitation().id(), false)));

        assertThat(outcomes).isIn(List.of(OK, CLOSED), List.of(INVALID, OK));
        assertThat(userAuditEntries()).isEqualTo(OK.equals(outcomes.getFirst()) ? 1 : 0);
    }

    @RepeatedTest(10)
    void parallelCreatesForOneEmailCreateOne() throws Exception {
        List<String> outcomes = inParallel(
                as(adminAuthentication(), () -> administration.create("ivan@x.test", Set.of(), false)),
                as(adminAuthentication(), () -> administration.create("IVAN@x.test", Set.of(Role.MENTOR), false)));

        assertThat(outcomes).containsExactlyInAnyOrder(OK, EXISTS);
        assertThat(users.count("invitations")).isEqualTo(1);
    }

    private IssuedInvitation invite(String email) {
        SecurityContextHolder.getContext().setAuthentication(adminAuthentication());
        try {
            return administration.create(email, Set.of(Role.MENTOR), false);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Authentication adminAuthentication() {
        return TestAuthentications.activeUserAuthentication(admin, Permission.USERS_INVITE);
    }

    private static Authentication caller(String subject) {
        return TestAuthentications.tokenAuthentication(AccessStatus.NOT_INVITED, null, subject, "ivan@x.test", true);
    }

    private void assertChangedOnce() {
        assertThat(users.count("users")).isEqualTo(2);
        assertThat(users.count("user_identities")).isEqualTo(1);
        assertThat(userAuditEntries()).isEqualTo(1);
    }

    private int userAuditEntries() {
        return jdbc.sql("select count(*) from audit_log where action = 'USER_INVITATION_ACCEPTED'")
                .query(Integer.class)
                .single();
    }

    private static String token(IssuedInvitation invited) {
        String path = invited.link().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static Callable<String> as(Authentication authentication, Runnable operation) {
        return () -> {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            try {
                operation.run();
                return OK;
            } catch (ConflictException exception) {
                return exception.type().toString();
            } catch (NotFoundException exception) {
                return exception.type().toString();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    /** Any other exception, such as a deadlock, fails the test. */
    @SafeVarargs
    private static List<String> inParallel(Callable<String>... operations) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(operations.length)) {
            for (Callable<String> operation : operations) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return operation.call();
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }
}
