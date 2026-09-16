package com.poultryflow.identity.membership.api;

import com.poultryflow.identity.membership.FarmAccessView;
import com.poultryflow.identity.membership.FarmMembershipService;
import com.poultryflow.identity.membership.FarmMembershipView;
import com.poultryflow.shared.api.ApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiContract.BUSINESS_API_BASE_PATH + "/farms/current")
public class FarmMembershipController {

    private final FarmMembershipService service;

    public FarmMembershipController(FarmMembershipService service) {
        this.service = service;
    }

    @GetMapping(value = "/membership", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@farmAuthorization.hasCurrentFarmAccess(authentication)")
    @Operation(summary = "Get the authenticated user's current farm access")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Current farm access",
                content = @Content(schema = @Schema(implementation = FarmAccessView.class))),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public FarmAccessView currentAccess(Authentication authentication) {
        return service.currentAccess(authentication);
    }

    @GetMapping(value = "/memberships", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@farmAuthorization.hasCurrentFarmRole(authentication, 'OWNER')")
    @Operation(summary = "List current farm memberships")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Farm memberships"),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "404", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public List<FarmMembershipView> list(Authentication authentication) {
        return service.list(authentication);
    }

    @PostMapping(
            value = "/memberships/invitations",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@farmAuthorization.hasCurrentFarmRole(authentication, 'OWNER')")
    @Operation(summary = "Invite a current farm user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Invitation recorded"),
        @ApiResponse(responseCode = "400", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "404", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public FarmMembershipView invite(
            @Valid @RequestBody InviteFarmMembershipRequest request,
            Authentication authentication) {
        return service.invite(authentication, request.email(), request.roles());
    }

    @PutMapping(
            value = "/memberships/{membershipId}/roles",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@farmAuthorization.hasCurrentFarmRole(authentication, 'OWNER')")
    @Operation(summary = "Replace a current farm membership's roles")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Membership roles updated"),
        @ApiResponse(responseCode = "400", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "404", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public FarmMembershipView updateRoles(
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateFarmMembershipRolesRequest request,
            Authentication authentication) {
        return service.updateRoles(authentication, membershipId, request.roles());
    }

    @PutMapping(
            value = "/memberships/{membershipId}/status",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@farmAuthorization.hasCurrentFarmRole(authentication, 'OWNER')")
    @Operation(summary = "Disable or re-enable a current farm membership")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Membership status updated"),
        @ApiResponse(responseCode = "400", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "404", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public FarmMembershipView updateStatus(
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateFarmMembershipStatusRequest request,
            Authentication authentication) {
        return service.setEnabled(authentication, membershipId, request.enabled());
    }
}
