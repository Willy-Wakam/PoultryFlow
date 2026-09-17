package com.poultryflow.farm.location.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poultryflow.farm.location.ActiveFarmLocationProvider;
import com.poultryflow.farm.location.ActiveFarmLocationRequiredException;
import com.poultryflow.farm.location.FarmLocationType;
import com.poultryflow.testing.PostgreSqlIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class FarmLocationIntegrationTests extends PostgreSqlIntegrationTest {

    private static final String LOCATIONS_PATH = "/api/v1/farms/current/locations";
    private static final UUID FARM_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ActiveFarmLocationProvider activeFarmLocationProvider;

    @BeforeEach
    void resetBusinessData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        jdbcTemplate.execute(
                "TRUNCATE farm_locations, farm_membership_roles, farm_memberships, "
                        + "audit_event_changed_fields, audit_events, farms CASCADE");
        insertFarm(FARM_ID, "Current Farm");
        insertMembership("owner-subject", "owner@example.com", "ACTIVE", "OWNER");
        insertMembership("manager-subject", "manager@example.com", "ACTIVE", "MANAGER");
        insertMembership("staff-subject", "staff@example.com", "ACTIVE", "STAFF");
        insertMembership("disabled-subject", "disabled@example.com", "DISABLED", "OWNER");
    }

    @ParameterizedTest
    @EnumSource(FarmLocationType.class)
    void managerCreatesEverySupportedLocationType(FarmLocationType type) throws Exception {
        mockMvc.perform(post(LOCATIONS_PATH)
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("  " + type + " Area  ", type)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.farmId").value(FARM_ID.toString()))
                .andExpect(jsonPath("$.name").value(type + " Area"))
                .andExpect(jsonPath("$.type").value(type.name()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));

        assertThat(auditActionCount("FARM_LOCATION_CREATED")).isEqualTo(1);
        assertThat(lastAuditFields()).containsExactly("name", "status", "type");
    }

    @Test
    void nameIsCaseInsensitiveWithinFarmButAllowedAcrossFarms() throws Exception {
        create("Brooder House", FarmLocationType.HOUSE, owner());

        mockMvc.perform(post(LOCATIONS_PATH)
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("  brooder house ", FarmLocationType.PEN)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FARM_LOCATION_NAME_ALREADY_EXISTS"));

        UUID otherFarmId = UUID.randomUUID();
        insertFarm(otherFarmId, "Other Farm");
        insertLocation(UUID.randomUUID(), otherFarmId, "BROODER HOUSE", "brooder house", "PEN", "ACTIVE");

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM farm_locations WHERE normalized_name = 'brooder house'",
                        Long.class))
                .isEqualTo(2);
    }

    @Test
    void editPersistsMeaningfulChangesAndIdenticalUpdateIsNoOp() throws Exception {
        UUID locationId = create("House A", FarmLocationType.HOUSE, owner());

        String response = mockMvc.perform(put(locationPath(locationId))
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Pen A", FarmLocationType.PEN, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(locationId.toString()))
                .andExpect(jsonPath("$.name").value("Pen A"))
                .andExpect(jsonPath("$.type").value("PEN"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(auditActionCount("FARM_LOCATION_UPDATED")).isEqualTo(1);
        assertThat(lastAuditFields()).containsExactly("name", "type");

        String repeated = mockMvc.perform(put(locationPath(locationId))
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Pen A", FarmLocationType.PEN, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(repeated).isEqualTo(response);
        assertThat(auditActionCount("FARM_LOCATION_UPDATED")).isEqualTo(1);
    }

    @Test
    void deactivateAndReactivateRetainLocationAndAuditOnlyChanges() throws Exception {
        UUID locationId = create("Store", FarmLocationType.STORAGE, owner());

        mockMvc.perform(put(statusPath(locationId))
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("INACTIVE", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(locationId.toString()))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get(LOCATIONS_PATH).with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(LOCATIONS_PATH)
                        .queryParam("includeInactive", "true")
                        .with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(locationId.toString()))
                .andExpect(jsonPath("$[0].status").value("INACTIVE"));

        mockMvc.perform(put(statusPath(locationId))
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("INACTIVE", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertThat(auditActionCount("FARM_LOCATION_DEACTIVATED")).isEqualTo(1);

        mockMvc.perform(put(statusPath(locationId))
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("ACTIVE", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(2));

        assertThat(count("farm_locations")).isEqualTo(1);
        assertThat(auditActionCount("FARM_LOCATION_ACTIVATED")).isEqualTo(1);
    }

    @Test
    void activeStaffListsActiveLocationsButCannotAdministerOrViewInactive() throws Exception {
        create("House A", FarmLocationType.HOUSE, owner());

        mockMvc.perform(get(LOCATIONS_PATH).with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("House A"));
        mockMvc.perform(get(LOCATIONS_PATH)
                        .queryParam("includeInactive", "true")
                        .with(staffWithGlobalOwnerRole()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));
        mockMvc.perform(post(LOCATIONS_PATH)
                        .with(staff())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Pen A", FarmLocationType.PEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAndUnknownSubjectsAreDeniedDespiteValidTokens() throws Exception {
        mockMvc.perform(get(LOCATIONS_PATH).with(disabled()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(LOCATIONS_PATH).with(user("unknown-subject")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(LOCATIONS_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeLocationBoundaryRequiresMatchingFarmAndActiveStatus() throws Exception {
        UUID locationId = create("House A", FarmLocationType.HOUSE, manager());
        UUID otherFarmId = UUID.randomUUID();
        UUID otherLocationId = UUID.randomUUID();
        insertFarm(otherFarmId, "Other Farm");
        insertLocation(otherLocationId, otherFarmId, "House A", "house a", "HOUSE", "ACTIVE");

        assertThat(activeFarmLocationProvider.requireActive(FARM_ID, locationId))
                .extracting("id", "farmId", "type")
                .containsExactly(locationId, FARM_ID, FarmLocationType.HOUSE);
        assertThatThrownBy(() -> activeFarmLocationProvider.requireActive(FARM_ID, otherLocationId))
                .isInstanceOf(ActiveFarmLocationRequiredException.class);

        jdbcTemplate.update(
                "UPDATE farm_locations SET status = 'INACTIVE' WHERE id = ?", locationId);
        assertThatThrownBy(() -> activeFarmLocationProvider.requireActive(FARM_ID, locationId))
                .isInstanceOf(ActiveFarmLocationRequiredException.class);
    }

    @Test
    void staleVersionUnknownLocationAndDuplicateEditReturnSafeProblems() throws Exception {
        UUID firstId = create("House A", FarmLocationType.HOUSE, owner());
        UUID secondId = create("Pen A", FarmLocationType.PEN, manager());

        mockMvc.perform(put(locationPath(firstId))
                        .with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("House B", FarmLocationType.HOUSE, 0)))
                .andExpect(status().isOk());
        mockMvc.perform(put(locationPath(firstId))
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("House C", FarmLocationType.HOUSE, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(put(locationPath(secondId))
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("HOUSE B", FarmLocationType.PEN, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_LOCATION_NAME_ALREADY_EXISTS"));
        mockMvc.perform(put(locationPath(UUID.randomUUID()))
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Missing", FarmLocationType.OTHER, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_LOCATION_NOT_FOUND"));
    }

    @Test
    void invalidRequestsUseValidationProblemContract() throws Exception {
        mockMvc.perform(post(LOCATIONS_PATH)
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"type\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[*].field", contains("name", "type")));
    }

    private UUID create(
            String name,
            FarmLocationType type,
            RequestPostProcessor actor) throws Exception {
        String response = mockMvc.perform(post(LOCATIONS_PATH)
                        .with(actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name, type)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private String createBody(String name, FarmLocationType type) {
        return """
                {"name":"%s","type":"%s"}
                """.formatted(name, type);
    }

    private String updateBody(String name, FarmLocationType type, long version) {
        return """
                {"name":"%s","type":"%s","version":%d}
                """.formatted(name, type, version);
    }

    private String statusBody(String status, long version) {
        return """
                {"status":"%s","version":%d}
                """.formatted(status, version);
    }

    private String locationPath(UUID locationId) {
        return LOCATIONS_PATH + "/" + locationId;
    }

    private String statusPath(UUID locationId) {
        return locationPath(locationId) + "/status";
    }

    private RequestPostProcessor owner() {
        return user("owner-subject");
    }

    private RequestPostProcessor manager() {
        return user("manager-subject");
    }

    private RequestPostProcessor staff() {
        return user("staff-subject");
    }

    private RequestPostProcessor disabled() {
        return user("disabled-subject");
    }

    private RequestPostProcessor staffWithGlobalOwnerRole() {
        return jwt().jwt(jwt -> jwt
                        .subject("staff-subject")
                        .claim("email", "staff@example.com")
                        .claim("email_verified", true))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private RequestPostProcessor user(String subject) {
        return jwt().jwt(jwt -> jwt
                        .subject(subject)
                        .claim("email", subject + "@example.com")
                        .claim("email_verified", true))
                .authorities(List.of());
    }

    private void insertFarm(UUID farmId, String name) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO farms (
                    id, name, timezone, country_code, currency_code, version,
                    created_at, updated_at
                ) VALUES (?, ?, 'Africa/Douala', 'CM', 'XAF', 0, ?, ?)
                """, farmId, name, Timestamp.from(now), Timestamp.from(now));
    }

    private void insertMembership(
            String subject,
            String email,
            String status,
            String role) {
        UUID membershipId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO farm_memberships (
                    id, farm_id, keycloak_subject, normalized_email, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 0, ?, ?)
                """, membershipId, FARM_ID, subject, email, status, Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update(
                "INSERT INTO farm_membership_roles (membership_id, role) VALUES (?, ?)",
                membershipId,
                role);
    }

    private void insertLocation(
            UUID id,
            UUID farmId,
            String name,
            String normalizedName,
            String type,
            String status) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO farm_locations (
                    id, farm_id, name, normalized_name, type, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, id, farmId, name, normalizedName, type, status, Timestamp.from(now), Timestamp.from(now));
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private long auditActionCount(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action_type = ?", Long.class, action);
    }

    private Collection<String> lastAuditFields() {
        return jdbcTemplate.queryForList("""
                SELECT field.field_name
                FROM audit_event_changed_fields field
                JOIN audit_events event ON event.id = field.audit_event_id
                WHERE event.id = (
                    SELECT id FROM audit_events ORDER BY occurred_at DESC LIMIT 1
                )
                ORDER BY field.position
                """, String.class);
    }
}
