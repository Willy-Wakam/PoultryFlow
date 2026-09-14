package com.poultryflow.identity.security;

import com.poultryflow.identity.access.PoultryFlowRole;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public final class KeycloakClientRoleAuthoritiesConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    static final String API_CLIENT_ID = "poultryflow-api";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object resourceAccessClaim = jwt.getClaim("resource_access");
        if (!(resourceAccessClaim instanceof Map<?, ?> resourceAccess)) {
            return List.of();
        }

        Object clientAccessClaim = resourceAccess.get(API_CLIENT_ID);
        if (!(clientAccessClaim instanceof Map<?, ?> clientAccess)) {
            return List.of();
        }

        Object rolesClaim = clientAccess.get("roles");
        if (!(rolesClaim instanceof Collection<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(PoultryFlowRole::fromTokenValue)
                .flatMap(Optional::stream)
                .distinct()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority()))
                .toList();
    }
}
