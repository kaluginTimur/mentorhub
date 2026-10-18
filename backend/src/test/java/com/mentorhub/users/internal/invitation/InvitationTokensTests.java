package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InvitationTokensTests {

    @Test
    void newTokensAre43Base64UrlCharactersAndDistinct() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = InvitationTokens.newToken();
            assertThat(token).matches("[A-Za-z0-9_-]{43}");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(100);
    }

    @Test
    void theHashIsLowercaseHexSha256OfTheToken() {
        assertThat(InvitationTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void aWellFormedTokenIsHashed() {
        String token = InvitationTokens.newToken();

        assertThat(InvitationTokens.hashIfWellFormed(token)).contains(InvitationTokens.hash(token));
    }

    // Spec, Tokens and links: anything else is invalid without a query.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "abc",
                "Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9wQ",
                "Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9wQeA",
                "Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9wQ=",
                "Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9w+/",
                "Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9w Q"
            })
    void aMalformedTokenHasNoHash(String token) {
        assertThat(InvitationTokens.hashIfWellFormed(token)).isEmpty();
    }
}
