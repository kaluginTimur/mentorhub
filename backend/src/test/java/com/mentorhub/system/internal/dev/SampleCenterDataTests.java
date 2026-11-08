package com.mentorhub.system.internal.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.support.EndpointTest;
import com.mentorhub.users.internal.security.TestIssuer;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks of the sample center data itself, not scenarios run on it. The data is plain SQL that bypasses the services,
 * so nothing but these tests notices a row the application cannot read back or a detail that disagrees with the row it
 * describes. They read the whole data set, so they stay valid as later features add their data. Same context as
 * {@link SampleCenterTests}.
 */
@EndpointTest
@ActiveProfiles("dev")
@Import(SampleCenterTests.LocalIssuer.class)
class SampleCenterDataTests {

    static final String ADMINISTRATOR = Personas.USERS.getFirst().subject();

    static final String FIXED_INVITATION_TOKEN = "sample-center-invitation-token-000000000001";

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    SampleCenter sampleCenter;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void start() {
        sampleCenter.restore();
    }

    // A history leaves out an entry it cannot interpret (AUD-6.4, AUD-6.5) instead of failing, so a sample entry with
    // a wrong action or malformed details would silently vanish from the screen.
    @Test
    void everyHistoryEntryOfEveryUserIsReadable() {
        for (UUID user : ids("users")) {
            List<UUID> stored = jdbc.sql("select id from audit_log where subject_type = 'USER' and subject_id = :user")
                    .param("user", user)
                    .query(UUID.class)
                    .list();

            assertThat(readAll("/api/v1/users/" + user + "/history"))
                    .as("history of user %s", user)
                    .containsExactlyInAnyOrderElementsOf(stored);
        }
    }

    @Test
    void theUserListReturnsEveryUser() {
        assertThat(readAll("/api/v1/users")).containsExactlyInAnyOrderElementsOf(ids("users"));
    }

    @Test
    void theInvitationListReturnsEveryInvitation() {
        assertThat(readAll("/api/v1/invitations")).containsExactlyInAnyOrderElementsOf(ids("invitations"));
    }

    // README documents this link for the persona Not invited.
    @Test
    void theFixedInvitationLinkPreviews() {
        MvcTestResult preview = mvc.post()
                .uri("/api/v1/invitations/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + FIXED_INVITATION_TOKEN + "\"}")
                .exchange();

        assertThat(preview).hasStatusOk();
        assertThat(preview).bodyJson().extractingPath("$.email").isEqualTo("stranger@mentorhub.local");
    }

    // The data set writes times inside details by hand, relative to the start; the expiry a reissue recorded must be
    // the expiry the invitation has, unless a later reissue replaced it.
    @Test
    void theNewestReissuedLinkOfAnInvitationExpiresWhenTheInvitationDoes() {
        List<Reissue> newest = jdbc.sql("""
                        select distinct on (a.subject_id)
                               a.subject_id, a.details ->> 'expiresAt' as recorded, i.expires_at
                        from audit_log a join invitations i on i.id = a.subject_id
                        where a.action = 'INVITATION_LINK_REISSUED' and a.subject_type = 'INVITATION'
                        order by a.subject_id, a.occurred_at desc
                        """)
                .query((row, i) -> new Reissue(
                        row.getObject("subject_id", UUID.class),
                        row.getString("recorded"),
                        row.getTimestamp("expires_at")))
                .list();

        assertThat(newest).isNotEmpty();
        for (Reissue reissue : newest) {
            assertThat(reissue.recorded())
                    .as("expiresAt of invitation %s", reissue.invitation())
                    .isNotNull();
            assertThat(Instant.parse(reissue.recorded()))
                    .as("expiresAt of invitation %s", reissue.invitation())
                    .isEqualTo(reissue.expiresAt().toInstant());
        }
    }

    record Reissue(UUID invitation, String recorded, Timestamp expiresAt) {}

    private List<UUID> ids(String table) {
        return jdbc.sql("select id from " + table).query(UUID.class).list();
    }

    // Every item id of a paged list, read page by page as a client does; the pages must add up to the total.
    private List<UUID> readAll(String path) {
        List<UUID> ids = new ArrayList<>();
        long total = -1;
        for (int page = 0; total < 0 || (long) page * 10 < total; page++) {
            MvcTestResult result = mvc.get()
                    .uri(path + "?size=10&page=" + page)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            issuer.token(ADMINISTRATOR)
                                    .claim("iss", Personas.LOCAL_ISSUER)
                                    .bearer())
                    .exchange();
            assertThat(result).as("%s page %d", path, page).hasStatusOk();
            JsonNode body = JSON.readTree(content(result));
            total = body.path("totalItems").asLong();
            body.path("items")
                    .valueStream()
                    .forEach(item -> ids.add(UUID.fromString(item.path("id").asString())));
        }
        assertThat(ids).as("%s: items on all pages", path).hasSize((int) total);
        return ids;
    }

    private static String content(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
