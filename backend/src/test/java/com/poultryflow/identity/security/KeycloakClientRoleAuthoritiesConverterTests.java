package com.poultryflow.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakClientRoleAuthoritiesConverterTests {

    private final KeycloakClientRoleAuthoritiesConverter converter =
            new KeycloakClientRoleAuthoritiesConverter();

    @Test
    void mapsKnownPoultryFlowApiClientRoles() {
        Jwt jwt = jwt(Map.of(
                "resource_access",
                Map.of(
                        "poultryflow-api",
                        Map.of("roles", List.of(
                                "OWNER",
                                "MANAGER",
                                "STAFF",
                                "ACCOUNTANT",
                                "VIEWER")))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(
                        "ROLE_OWNER",
                        "ROLE_MANAGER",
                        "ROLE_STAFF",
                        "ROLE_ACCOUNTANT",
                        "ROLE_VIEWER");
    }

    @Test
    void ignoresRealmRolesAndRolesFromOtherClients() {
        Jwt jwt = jwt(Map.of(
                "realm_access",
                Map.of("roles", List.of("OWNER")),
                "resource_access",
                Map.of("another-client", Map.of("roles", List.of("MANAGER")))));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void ignoresUnknownRolesWithoutDiscardingKnownRoles() {
        Jwt jwt = jwt(Map.of(
                "resource_access",
                Map.of(
                        "poultryflow-api",
                        Map.of("roles", List.of("VIEWER", "SUPERUSER", "viewer")))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_VIEWER");
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claims(jwtClaims -> jwtClaims.putAll(claims))
                .build();
    }
}
