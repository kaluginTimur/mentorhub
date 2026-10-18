package com.mentorhub.users.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.DisableReason;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.ConflictException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;

/** AC-15: parallel changes that can remove an administrator never leave the installation without one. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LockoutConcurrencyTests {

    private static final String OK = "OK";

    @Autowired
    UserAdministrationService service;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;
    UUID adminA;
    UUID adminB;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        adminA = users.create("a@x.test", UserStatus.ACTIVE, Role.ADMIN);
        adminB = users.create("b@x.test", UserStatus.ACTIVE, Role.ADMIN);
    }

    // Repeated because a race shows only in some runs.
    @RepeatedTest(10)
    void administratorsDisablingEachOtherLeaveOneActive() throws Exception {
        List<String> outcomes = inParallel(
                as(adminA, service -> service.disable(adminB, DisableReason.SECURITY, null)),
                as(adminB, service -> service.disable(adminA, DisableReason.SECURITY, null)));

        assertThat(outcomes).containsExactlyInAnyOrder(OK, LockoutProblems.LAST_ADMIN.toString());
        assertThat(activeAdministrators()).isEqualTo(1);
    }

    // The disable plus set-roles pair, which take the locks in the same order.
    @RepeatedTest(10)
    void disableAndRoleRemovalInParallelLeaveOneActive() throws Exception {
        List<String> outcomes = inParallel(
                as(adminA, service -> service.disable(adminB, DisableReason.SECURITY, null)),
                as(adminB, service -> service.setRoles(adminA, Set.of(Role.MENTOR))));

        assertThat(outcomes).containsExactlyInAnyOrder(OK, LockoutProblems.LAST_ADMIN.toString());
        assertThat(activeAdministrators()).isEqualTo(1);
    }

    @RepeatedTest(10)
    void disableAndRoleRemovalOfTheSameAdministratorBothSucceed() throws Exception {
        List<String> outcomes = inParallel(
                as(adminA, service -> service.disable(adminB, DisableReason.SECURITY, null)),
                as(adminA, service -> service.setRoles(adminB, Set.of())));

        assertThat(outcomes).containsExactly(OK, OK);
        assertThat(activeAdministrators()).isEqualTo(1);
    }

    private Callable<String> as(UUID caller, Consumer<UserAdministrationService> change) {
        return () -> {
            SecurityContextHolder.getContext()
                    .setAuthentication(TestAuthentications.activeUserAuthentication(
                            caller, Permission.USERS_READ, Permission.USERS_MANAGE));
            try {
                change.accept(service);
                return OK;
            } catch (ConflictException exception) {
                return exception.type().toString();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    /** Any other exception, such as a deadlock, fails the test. */
    @SafeVarargs
    private static List<String> inParallel(Callable<String>... changes) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(changes.length)) {
            for (Callable<String> change : changes) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return change.call();
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

    private int activeAdministrators() {
        return jdbc.sql("""
                        select count(*) from users u join user_roles ur on ur.user_id = u.id
                        where ur.role = 'ADMIN' and u.status = 'ACTIVE'
                        """).query(Integer.class).single();
    }
}
