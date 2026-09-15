package com.poultryflow.farm.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poultryflow.testing.PostgreSqlIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class FarmProfileIntegrationTests extends PostgreSqlIntegrationTest {

    private static final String FARM_PATH = "/api/v1/farms/current";
    private static final String VALID_PROFILE = """
            {
              "name": "Ferme Mvog-Betsi",
              "contactEmail": "owner@example.com",
              "contactPhone": "+237 600 000 000",
              "timezone": "Africa/Douala",
              "countryCode": "cm",
              "currencyCode": "xaf"
            }
            """;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void clearBusinessData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        jdbcTemplate.execute(
                "TRUNCATE audit_event_changed_fields, audit_events, farms CASCADE");
    }

    @Test
    void returnsNotConfiguredProblemWhenProfileIsMissing() throws Exception {
        mockMvc.perform(get(FARM_PATH).with(owner()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FARM_PROFILE_NOT_CONFIGURED"));
    }

    @Test
    void ownerCreatesProfileWithCanonicalDefaultsAndAuditActor() throws Exception {
        String withoutCountryAndCurrency = """
                {
                  "name": "  Ferme Mvog-Betsi  ",
                  "contactEmail": "owner@example.com",
                  "contactPhone": "  +237 600 000 000  ",
                  "timezone": "Africa/Douala"
                }
                """;

        mockMvc.perform(put(FARM_PATH)
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withoutCountryAndCurrency))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ferme Mvog-Betsi"))
                .andExpect(jsonPath("$.contactPhone").value("+237 600 000 000"))
                .andExpect(jsonPath("$.timezone").value("Africa/Douala"))
                .andExpect(jsonPath("$.countryCode").value("CM"))
                .andExpect(jsonPath("$.currencyCode").value("XAF"))
                .andExpect(jsonPath("$.version").value(0));

        assertThat(count("farms")).isEqualTo(1);
        assertThat(count("audit_events")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT action_type FROM audit_events", String.class))
                .isEqualTo("FARM_PROFILE_CREATED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT actor_subject FROM audit_events", String.class))
                .isEqualTo("owner-subject");
        assertThat(auditFields()).containsExactly(
                "contactEmail",
                "contactPhone",
                "countryCode",
                "currencyCode",
                "name",
                "timezone");
    }

    @Test
    void ownerReadsUpdatesAndCanRepeatIdenticalPutWithoutAnotherWriteEvent()
            throws Exception {
        createProfile();

        mockMvc.perform(get(FARM_PATH).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ferme Mvog-Betsi"))
                .andExpect(jsonPath("$.countryCode").value("CM"));

        String update = """
                {
                  "name": "PoultryFlow Farm",
                  "contactEmail": "owner@example.com",
                  "contactPhone": "+237 600 000 000",
                  "timezone": "Africa/Douala",
                  "countryCode": "CA",
                  "currencyCode": "CAD"
                }
                """;
        String updatedAt = mockMvc.perform(put(FARM_PATH)
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(count("farms")).isEqualTo(1);
        assertThat(count("audit_events")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT action_type FROM audit_events ORDER BY occurred_at DESC LIMIT 1",
                        String.class))
                .isEqualTo("FARM_PROFILE_UPDATED");
        assertThat(auditFieldsForLastEvent()).containsExactly(
                "countryCode", "currencyCode", "name");

        String repeated = mockMvc.perform(put(FARM_PATH)
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(repeated).isEqualTo(updatedAt);
        assertThat(count("farms")).isEqualTo(1);
        assertThat(count("audit_events")).isEqualTo(2);
    }

    @Test
    void acceptsRealIanaCountryAndCurrencyValues() throws Exception {
        String canadianProfile = """
                {
                  "name": "Ontario Farm",
                  "timezone": "America/Toronto",
                  "countryCode": "ca",
                  "currencyCode": "cad"
                }
                """;

        mockMvc.perform(put(FARM_PATH)
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canadianProfile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("America/Toronto"))
                .andExpect(jsonPath("$.countryCode").value("CA"))
                .andExpect(jsonPath("$.currencyCode").value("CAD"));
    }

    @ParameterizedTest
    @MethodSource("invalidProfiles")
    void rejectsInvalidProfileFields(String body, String field) throws Exception {
        mockMvc.perform(put(FARM_PATH)
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[*].field", contains(field)));
    }

    @Test
    void requiresAuthenticationAndOwnerRole() throws Exception {
        mockMvc.perform(get(FARM_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(put(FARM_PATH)
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PROFILE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));
    }

    @Test
    void failsClosedWhenMoreThanOneFarmExists() throws Exception {
        insertFarm(UUID.randomUUID(), "First farm", Instant.parse("2026-01-01T00:00:00Z"));
        insertFarm(UUID.randomUUID(), "Second farm", Instant.parse("2026-01-02T00:00:00Z"));

        mockMvc.perform(get(FARM_PATH).with(owner()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_PROFILE_AMBIGUOUS"));
    }

    @Test
    void databaseRejectsAuditMutation() throws Exception {
        createProfile();

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbcTemplate.update(
                        "UPDATE audit_events SET actor_subject = 'replacement'")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit records are append-only");
    }

    private static Stream<Arguments> invalidProfiles() {
        return Stream.of(
                Arguments.of(profileWith("name", "\"\""), "name"),
                Arguments.of(profileWith("contactEmail", "\"not-an-email\""), "contactEmail"),
                Arguments.of(profileWith("timezone", "\"Mars/Olympus\""), "timezone"),
                Arguments.of(profileWith("countryCode", "\"ZZ\""), "countryCode"),
                Arguments.of(profileWith("currencyCode", "\"ZZZ\""), "currencyCode"));
    }

    private static String profileWith(String field, String value) {
        return """
                {
                  "name": "Valid Farm",
                  "contactEmail": "owner@example.com",
                  "timezone": "Africa/Douala",
                  "countryCode": "CM",
                  "currencyCode": "XAF",
                  "%s": %s
                }
                """.formatted(field, value);
    }

    private void createProfile() throws Exception {
        mockMvc.perform(put(FARM_PATH)
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PROFILE))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor owner() {
        return jwt().jwt(jwt -> jwt.subject("owner-subject"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private RequestPostProcessor manager() {
        return jwt().jwt(jwt -> jwt.subject("manager-subject"))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private java.util.List<String> auditFields() {
        return jdbcTemplate.queryForList(
                "SELECT field_name FROM audit_event_changed_fields ORDER BY position",
                String.class);
    }

    private java.util.List<String> auditFieldsForLastEvent() {
        return jdbcTemplate.queryForList("""
                SELECT fields.field_name
                FROM audit_event_changed_fields fields
                JOIN audit_events event ON event.id = fields.audit_event_id
                WHERE event.id = (SELECT id FROM audit_events ORDER BY occurred_at DESC LIMIT 1)
                ORDER BY fields.position
                """, String.class);
    }

    private void insertFarm(UUID id, String name, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO farms (
                    id, name, timezone, country_code, currency_code, version,
                    created_at, updated_at
                ) VALUES (?, ?, 'Africa/Douala', 'CM', 'XAF', 0, ?, ?)
                """, id, name, Timestamp.from(createdAt), Timestamp.from(createdAt));
    }
}
