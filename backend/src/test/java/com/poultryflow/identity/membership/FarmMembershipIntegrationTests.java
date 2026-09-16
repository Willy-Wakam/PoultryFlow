package com.poultryflow.identity.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poultryflow.identity.access.PoultryFlowRole;
import com.poultryflow.testing.PostgreSqlIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class FarmMembershipIntegrationTests extends PostgreSqlIntegrationTest {

    private static final String MEMBERSHIP_PATH = "/api/v1/farms/current/membership";
    private static final String MEMBERSHIPS_PATH = "/api/v1/farms/current/memberships";
    private static final String INVITATIONS_PATH = MEMBERSHIPS_PATH + "/invitations";
    private static final String FARM_PATH = "/api/v1/farms/current";
    private static final String BOOTSTRAP_SUBJECT = "bootstrap-owner-subject";
    private static final String BOOTSTRAP_EMAIL = "owner@example.com";

    private final UUID farmId = UUID.randomUUID();
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FarmMembershipService membershipService;

    @BeforeEach
    void resetBusinessData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        jdbcTemplate.execute(
                "TRUNCATE farm_membership_roles, farm_memberships, "
                        + "audit_event_changed_fields, audit_events, farms CASCADE");
        insertFarm();
    }

    @Test
    void firstMutationBootstrapsOwnerAndCreatesNormalizedInvitation() throws Exception {
        mockMvc.perform(post(INVITATIONS_PATH)
                        .with(globalOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitation("  STAFF@Example.COM  ", "STAFF")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("staff@example.com"))
                .andExpect(jsonPath("$.status").value("INVITED"))
                .andExpect(jsonPath("$.roles[0]").value("STAFF"));

        assertThat(count("farm_memberships")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM farm_memberships membership
                        JOIN farm_membership_roles role ON role.membership_id = membership.id
                        WHERE membership.farm_id = ?
                          AND membership.keycloak_subject = ?
                          AND membership.normalized_email = ?
                          AND membership.status = 'ACTIVE'
                          AND role.role = 'OWNER'
                        """, Long.class, farmId, BOOTSTRAP_SUBJECT, BOOTSTRAP_EMAIL))
                .isEqualTo(1);
        assertThat(auditActionCount("FARM_MEMBERSHIP_OWNER_BOOTSTRAPPED")).isEqualTo(1);
        assertThat(auditActionCount("FARM_MEMBERSHIP_INVITED")).isEqualTo(1);
    }

    @Test
    void verifiedMatchingEmailClaimsInvitationAndAuditsSubjectBinding() throws Exception {
        UUID invitationId = bootstrapAndInvite("invitee@example.com", "STAFF");

        mockMvc.perform(get(MEMBERSHIP_PATH).with(user(
                        "invitee-subject", "INVITEE@example.com", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipId").value(invitationId.toString()))
                .andExpect(jsonPath("$.roles[0]").value("STAFF"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bootstrapAuthority").value(false));

        assertThat(jdbcTemplate.queryForMap(
                        "SELECT keycloak_subject, status FROM farm_memberships WHERE id = ?",
                        invitationId))
                .containsEntry("keycloak_subject", "invitee-subject")
                .containsEntry("status", "ACTIVE");
        assertThat(auditActionCount("FARM_MEMBERSHIP_INVITATION_CLAIMED")).isEqualTo(1);
        assertThat(lastAuditFields()).containsExactly("keycloakSubject", "status");
    }

    @Test
    void wrongOrUnverifiedEmailCannotClaimInvitation() throws Exception {
        UUID invitationId = bootstrapAndInvite("invitee@example.com", "STAFF");

        mockMvc.perform(get(MEMBERSHIP_PATH).with(user(
                        "wrong-subject", "other@example.com", true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));
        mockMvc.perform(get(MEMBERSHIP_PATH).with(user(
                        "unverified-subject", "invitee@example.com", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));

        assertThat(jdbcTemplate.queryForMap(
                        "SELECT keycloak_subject, status FROM farm_memberships WHERE id = ?",
                        invitationId))
                .containsEntry("keycloak_subject", null)
                .containsEntry("status", "INVITED");
    }

    @Test
    void membershipOwnerWorksWithoutMatchingKeycloakRole() throws Exception {
        UUID membershipId = bootstrapAndInvite("second-owner@example.com", "OWNER");
        RequestPostProcessor secondOwner = user(
                "second-owner-subject", "second-owner@example.com", true);
        mockMvc.perform(get(MEMBERSHIP_PATH).with(secondOwner))
                .andExpect(status().isOk());

        mockMvc.perform(get(MEMBERSHIPS_PATH).with(secondOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]", membershipId).exists());
        mockMvc.perform(get(FARM_PATH).with(secondOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(farmId.toString()));
    }

    @Test
    void membershipRolesOverrideGlobalTokenOwnerRole() throws Exception {
        insertMembership(
                UUID.randomUUID(),
                "manager-subject",
                "manager@example.com",
                "ACTIVE",
                "MANAGER");

        mockMvc.perform(get(MEMBERSHIPS_PATH).with(userWithGlobalOwner(
                        "manager-subject", "manager@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));
        mockMvc.perform(put(FARM_PATH)
                        .with(userWithGlobalOwner("manager-subject", "manager@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFarmProfile()))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledUserWithValidTokenCannotAccessFarmOrClaimAnotherInvite() throws Exception {
        UUID disabledId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        insertMembership(
                disabledId,
                "disabled-subject",
                "disabled@example.com",
                "DISABLED",
                "STAFF");
        insertMembership(
                invitationId,
                null,
                "other-invite@example.com",
                "INVITED",
                "OWNER");
        RequestPostProcessor disabledToken = user(
                "disabled-subject", "other-invite@example.com", true);

        mockMvc.perform(get(MEMBERSHIP_PATH).with(disabledToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(FARM_PATH).with(disabledToken))
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT keycloak_subject FROM farm_memberships WHERE id = ?",
                        String.class,
                        invitationId))
                .isNull();
    }

    @Test
    void ownerListsInvitedActiveAndDisabledMemberships() throws Exception {
        UUID activeId = UUID.randomUUID();
        UUID invitedId = UUID.randomUUID();
        UUID disabledId = UUID.randomUUID();
        insertMembership(
                activeId, BOOTSTRAP_SUBJECT, BOOTSTRAP_EMAIL, "ACTIVE", "OWNER");
        insertMembership(invitedId, null, "invited@example.com", "INVITED", "STAFF");
        insertMembership(
                disabledId, "disabled-subject", "disabled@example.com", "DISABLED", "VIEWER");

        mockMvc.perform(get(MEMBERSHIPS_PATH).with(user(
                        BOOTSTRAP_SUBJECT, BOOTSTRAP_EMAIL, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.status == 'INVITED')]").exists())
                .andExpect(jsonPath("$[?(@.status == 'ACTIVE')]").exists())
                .andExpect(jsonPath("$[?(@.status == 'DISABLED')]").exists());
    }

    @Test
    void roleChangeIsImmediateAndIdenticalUpdateIsNoOp() throws Exception {
        UUID membershipId = bootstrapAndInvite("worker@example.com", "STAFF");
        RequestPostProcessor worker = user("worker-subject", "worker@example.com", true);
        mockMvc.perform(get(MEMBERSHIP_PATH).with(worker)).andExpect(status().isOk());

        String rolesPath = MEMBERSHIPS_PATH + "/" + membershipId + "/roles";
        mockMvc.perform(put(rolesPath)
                        .with(globalOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"MANAGER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"));
        mockMvc.perform(get(MEMBERSHIP_PATH).with(worker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"));
        long eventCount = auditActionCount("FARM_MEMBERSHIP_ROLES_CHANGED");

        mockMvc.perform(put(rolesPath)
                        .with(globalOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"MANAGER\"]}"))
                .andExpect(status().isOk());

        assertThat(auditActionCount("FARM_MEMBERSHIP_ROLES_CHANGED"))
                .isEqualTo(eventCount);
    }

    @Test
    void disableAndReEnableAreSoftAndAudited() throws Exception {
        UUID membershipId = bootstrapAndInvite("worker@example.com", "STAFF");
        RequestPostProcessor worker = user("worker-subject", "worker@example.com", true);
        mockMvc.perform(get(MEMBERSHIP_PATH).with(worker)).andExpect(status().isOk());
        String statusPath = MEMBERSHIPS_PATH + "/" + membershipId + "/status";

        mockMvc.perform(put(statusPath)
                        .with(globalOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        mockMvc.perform(get(MEMBERSHIP_PATH).with(worker))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(statusPath)
                        .with(globalOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(count("farm_memberships")).isEqualTo(2);
        assertThat(auditActionCount("FARM_MEMBERSHIP_DISABLED")).isEqualTo(1);
        assertThat(auditActionCount("FARM_MEMBERSHIP_RE_ENABLED")).isEqualTo(1);
    }

    @Test
    void lastActiveOwnerCannotBeDemotedOrDisabled() throws Exception {
        bootstrapAndInvite("worker@example.com", "STAFF");
        UUID ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM farm_memberships WHERE keycloak_subject = ?",
                UUID.class,
                BOOTSTRAP_SUBJECT);

        mockMvc.perform(put(MEMBERSHIPS_PATH + "/" + ownerId + "/roles")
                        .with(globalOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"MANAGER\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_OWNER_REQUIRED"));
        mockMvc.perform(put(MEMBERSHIPS_PATH + "/" + ownerId + "/status")
                        .with(globalOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_OWNER_REQUIRED"));
    }

    @Test
    void concurrentDemotionsCannotRemoveBothActiveOwners() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        insertMembership(firstId, "first-owner", "first@example.com", "ACTIVE", "OWNER");
        insertMembership(secondId, "second-owner", "second@example.com", "ACTIVE", "OWNER");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> demoteConcurrently(
                    authentication("first-owner", "first@example.com"),
                    firstId,
                    ready,
                    start));
            var second = executor.submit(() -> demoteConcurrently(
                    authentication("second-owner", "second@example.com"),
                    secondId,
                    ready,
                    start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("UPDATED", "LAST_OWNER_BLOCKED");
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM farm_memberships membership
                        JOIN farm_membership_roles role ON role.membership_id = membership.id
                        WHERE membership.status = 'ACTIVE' AND role.role = 'OWNER'
                        """, Long.class))
                .isEqualTo(1);
    }

    @Test
    void nonOwnerCannotAdministerMemberships() throws Exception {
        insertMembership(
                UUID.randomUUID(),
                "manager-subject",
                "manager@example.com",
                "ACTIVE",
                "MANAGER");
        RequestPostProcessor manager = user(
                "manager-subject", "manager@example.com", true);

        mockMvc.perform(get(MEMBERSHIPS_PATH).with(manager))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(INVITATIONS_PATH)
                        .with(manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitation("new@example.com", "STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateInvitationAndInvalidInputReturnSafeProblems() throws Exception {
        bootstrapAndInvite("worker@example.com", "STAFF");

        mockMvc.perform(post(INVITATIONS_PATH)
                        .with(globalOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitation("WORKER@example.com", "VIEWER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_MEMBERSHIP_ALREADY_EXISTS"));
        mockMvc.perform(post(INVITATIONS_PATH)
                        .with(globalOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String demoteConcurrently(
            Authentication authentication,
            UUID membershipId,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException, ExecutionException {
        ready.countDown();
        start.await();
        try {
            membershipService.updateRoles(
                    authentication, membershipId, Set.of(PoultryFlowRole.MANAGER));
            return "UPDATED";
        } catch (LastActiveOwnerException exception) {
            return "LAST_OWNER_BLOCKED";
        }
    }

    private UUID bootstrapAndInvite(String email, String role) throws Exception {
        String response = mockMvc.perform(post(INVITATIONS_PATH)
                        .with(globalOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitation(email, role)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private String invitation(String email, String role) {
        return """
                {"email":"%s","roles":["%s"]}
                """.formatted(email, role);
    }

    private RequestPostProcessor globalOwner() {
        return jwt().jwt(jwt -> jwt
                        .subject(BOOTSTRAP_SUBJECT)
                        .claim("email", BOOTSTRAP_EMAIL)
                        .claim("email_verified", true))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private RequestPostProcessor user(String subject, String email, boolean verified) {
        return jwt().jwt(jwt -> jwt
                        .subject(subject)
                        .claim("email", email)
                        .claim("email_verified", verified))
                .authorities(List.of());
    }

    private RequestPostProcessor userWithGlobalOwner(String subject, String email) {
        return jwt().jwt(jwt -> jwt
                        .subject(subject)
                        .claim("email", email)
                        .claim("email_verified", true))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private Authentication authentication(String subject, String email) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("email", email)
                .claim("email_verified", true)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        return new JwtAuthenticationToken(jwt, List.of());
    }

    private void insertFarm() {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO farms (
                    id, name, timezone, country_code, currency_code, version,
                    created_at, updated_at
                ) VALUES (?, 'Test Farm', 'Africa/Douala', 'CM', 'XAF', 0, ?, ?)
                """, farmId, Timestamp.from(now), Timestamp.from(now));
    }

    private void insertMembership(
            UUID id,
            String subject,
            String email,
            String status,
            String... roles) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO farm_memberships (
                    id, farm_id, keycloak_subject, normalized_email, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 0, ?, ?)
                """, id, farmId, subject, email, status, Timestamp.from(now), Timestamp.from(now));
        for (String role : roles) {
            jdbcTemplate.update(
                    "INSERT INTO farm_membership_roles (membership_id, role) VALUES (?, ?)",
                    id,
                    role);
        }
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
                ORDER BY event.occurred_at DESC, field.position
                LIMIT 2
                """, String.class);
    }

    private String validFarmProfile() {
        return """
                {
                  "name":"Updated Farm",
                  "timezone":"Africa/Douala",
                  "countryCode":"CM",
                  "currencyCode":"XAF"
                }
                """;
    }
}
