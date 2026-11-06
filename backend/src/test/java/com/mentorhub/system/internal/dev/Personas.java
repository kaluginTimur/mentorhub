package com.mentorhub.system.internal.dev;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

/** The persona table of DEV-1: accounts of the local Keycloak, matched by their fixed ids. */
public final class Personas {

    public static final String LOCAL_ISSUER = "http://localhost:8180/realms/mentorhub";

    public static final String NOT_INVITED = "00000000-0000-4000-8000-000000000006";

    /** Every persona that is a development user, that is all but Not invited. */
    public static final List<Persona> USERS = List.of(
            new Persona("Administrator", "00000000-0000-4000-8000-000000000001", "ACTIVE", List.of("ADMIN")),
            new Persona("Coordinator", "00000000-0000-4000-8000-000000000007", "ACTIVE", List.of("COORDINATOR")),
            new Persona("Mentor", "00000000-0000-4000-8000-000000000002", "ACTIVE", List.of("MENTOR")),
            new Persona("Student", "00000000-0000-4000-8000-000000000003", "ACTIVE", List.of("STUDENT")),
            new Persona("Observer", "00000000-0000-4000-8000-000000000008", "ACTIVE", List.of("OBSERVER")),
            new Persona(
                    "Administrator and mentor",
                    "00000000-0000-4000-8000-000000000009",
                    "ACTIVE",
                    List.of("ADMIN", "MENTOR")),
            new Persona(
                    "Mentor and coordinator",
                    "00000000-0000-4000-8000-00000000000a",
                    "ACTIVE",
                    List.of("MENTOR", "COORDINATOR")),
            new Persona("Without roles", "00000000-0000-4000-8000-000000000004", "ACTIVE", List.of()),
            new Persona("Disabled", "00000000-0000-4000-8000-000000000005", "DISABLED", List.of("MENTOR")));

    public static final List<String> ALL_SUBJECTS = Stream.concat(
                    USERS.stream().map(Persona::subject), Stream.of(NOT_INVITED))
            .toList();

    /** The local Keycloak's realm, relative to {@code backend/}, where the tests run. */
    public static final Path REALM = Path.of("../infra/keycloak/mentorhub-realm.json");

    private Personas() {}

    /** The users of the realm: id to email, in the order of the file. */
    public static Map<String, String> realmEmailsById() {
        return JsonMapper.builder()
                .build()
                .readTree(REALM.toFile())
                .path("users")
                .valueStream()
                .collect(Collectors.toMap(
                        user -> user.path("id").asString(),
                        user -> user.path("email").asString(),
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    public record Persona(String name, String subject, String status, List<String> roles) {}
}
