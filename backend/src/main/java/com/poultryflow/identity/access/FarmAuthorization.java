package com.poultryflow.identity.access;

import com.poultryflow.identity.membership.FarmMembershipAccessDeniedException;
import com.poultryflow.identity.membership.FarmMembershipService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("farmAuthorization")
public class FarmAuthorization {

    private final FarmMembershipService membershipService;

    public FarmAuthorization(FarmMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    public boolean hasCurrentFarmRole(Authentication authentication, String role) {
        try {
            return membershipService.hasCurrentRole(
                    authentication, PoultryFlowRole.valueOf(role));
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
