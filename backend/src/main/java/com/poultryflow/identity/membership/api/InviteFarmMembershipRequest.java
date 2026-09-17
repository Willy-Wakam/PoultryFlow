package com.poultryflow.identity.membership.api;

import com.poultryflow.identity.access.PoultryFlowRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record InviteFarmMembershipRequest(
        @NotBlank(message = "is required")
        @Email(message = "must be a valid email address")
        @Size(max = 254, message = "must be at most 254 characters")
        String email,

        @NotEmpty(message = "must contain at least one role")
        Set<@NotNull PoultryFlowRole> roles) {

    public InviteFarmMembershipRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
