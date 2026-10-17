package com.mentorhub.users.internal.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * A trusted issuer for tests: signs tokens with a local RSA key and serves the public key as a JWK set over HTTP, so
 * the application validates tokens through its real decoder, converter, bootstrap and gate.
 */
public final class TestIssuer implements AutoCloseable {

    public static final String ISSUER = "https://issuer.test/realms/mentorhub";
    public static final String AUDIENCE = "mentorhub-api";
    public static final String BOOTSTRAP_EMAIL = "bootstrap@mentorhub.test";

    /** A second trusted issuer whose keys cannot be loaded: its JWK set URI refuses connections. */
    public static final String UNREACHABLE_ISSUER = "https://unreachable.test/realms/mentorhub";

    public static final String UNREACHABLE_JWK_SET_URI = "http://127.0.0.1:9/jwks";

    /** A third trusted issuer whose JWK set endpoint accepts connections and never answers. */
    public static final String HANGING_ISSUER = "https://hanging.test/realms/mentorhub";

    private final RSAKey key = generateKey("test-key");
    private final RSAKey unknownKey = generateKey("unknown-key");
    private final HttpServer server;
    private final ServerSocket hangingServer;
    private final List<Socket> heldConnections = new CopyOnWriteArrayList<>();

    TestIssuer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(jwks);
            }
        });
        server.start();
        try {
            hangingServer = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Thread.ofPlatform().daemon().name("hanging-jwks").start(() -> {
            while (!hangingServer.isClosed()) {
                try {
                    heldConnections.add(hangingServer.accept());
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    public String hangingJwkSetUri() {
        return "http://127.0.0.1:" + hangingServer.getLocalPort() + "/jwks";
    }

    public String jwkSetUri() {
        return "http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort() + "/jwks";
    }

    /** A valid token of the trusted issuer for the MentorHub audience, without profile claims. */
    public Token token(String subject) {
        return new Token(subject);
    }

    @Override
    public void close() {
        server.stop(0);
        try {
            hangingServer.close();
            for (Socket connection : heldConnections) {
                connection.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public final class Token {

        private final Map<String, Object> claims = new LinkedHashMap<>();
        private RSAKey signingKey = key;

        private Token(String subject) {
            Instant now = Instant.now();
            claims.put("iss", ISSUER);
            claims.put("sub", subject);
            claims.put("aud", List.of(AUDIENCE));
            claims.put("iat", now);
            claims.put("exp", now.plus(Duration.ofMinutes(5)));
        }

        public Token claim(String name, Object value) {
            claims.put(name, value);
            return this;
        }

        public Token without(String name) {
            claims.remove(name);
            return this;
        }

        public Token email(String email, boolean verified) {
            return claim("email", email).claim("email_verified", verified);
        }

        public Token expired() {
            Instant now = Instant.now();
            return claim("iat", now.minus(Duration.ofHours(2))).claim("exp", now.minus(Duration.ofHours(1)));
        }

        public Token signedWithUnknownKey() {
            signingKey = unknownKey;
            return this;
        }

        public String encode() {
            var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                    .keyId(signingKey.getKeyID())
                    .build();
            JwtClaimsSet claimsSet =
                    JwtClaimsSet.builder().claims(c -> c.putAll(claims)).build();
            return encoder.encode(JwtEncoderParameters.from(header, claimsSet)).getTokenValue();
        }

        public String bearer() {
            return "Bearer " + encode();
        }
    }
}
