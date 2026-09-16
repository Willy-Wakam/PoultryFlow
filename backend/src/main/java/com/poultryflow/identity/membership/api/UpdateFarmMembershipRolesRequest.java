package com.poultryflow.identity.membership.api;

import com.poultryflow.identity.access.PoultryFlowRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateFarmMembershipRolesRequest(
        @NotEmpty(message = "must contain at least one role")
        Set<@NotNull PoultryFlowRole> roles) {
}
