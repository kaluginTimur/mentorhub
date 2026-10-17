package com.mentorhub.users.internal.bootstrap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mentorhub.users.internal.persistence.UserRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;

class AdministratorBootstrapEligibilityTests {

    // AC-15
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void ruleIsOffWithoutBootstrapEmail(@Nullable String configuredEmail) {
        UserRepository users = mock(UserRepository.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        var bootstrap = new AdministratorBootstrap(users, transactions, configuredEmail);

        bootstrap.applyIfEligible(
                new SignInClaims("https://issuer.test", "subject", "admin@example.com", true, "Anna", null));

        verifyNoInteractions(users, transactions);
    }
}
