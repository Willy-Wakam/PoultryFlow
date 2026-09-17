package com.poultryflow.identity.access;

import com.poultryflow.identity.membership.FarmMembershipAccessDeniedException;
import com.poultryflow.identity.membership.FarmMembershipService;
import java.util.Arrays;
import java.util.EnumSet;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("farmAuthorization")
public class FarmAuthorization {

    private final FarmMembershipService membershipService;

    public FarmAuthorization(FarmMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    public boolean hasCurrentFarmRole(Authentication authentication, String role) {
        return hasAnyCurrentFarmRole(authentication, role);
    }

    public boolean hasAnyCurrentFarmRole(Authentication authentication, String... roles) {
        try {
            EnumSet<PoultryFlowRole> required = Arrays.stream(roles)
                    .map(PoultryFlowRole::valueOf)
                    .collect(
                            () -> EnumSet.noneOf(PoultryFlowRole.class),
                            EnumSet::add,
                            EnumSet::addAll);
            return !required.isEmpty()
                    && membershipService.hasAnyCurrentRole(authentication, required);
        } catch (FarmMembershipAccessDeniedException exception) {
            return false;
        }
    }

    public boolean hasCurrentFarmAccess(Authentication authentication) {
        try {
            return membershipService.hasCurrentAccess(authentication);
        } catch (FarmMembershipAccessDeniedException exception) {
            return false;
        }
    }
}
