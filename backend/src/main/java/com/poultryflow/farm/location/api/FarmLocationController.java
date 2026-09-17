package com.poultryflow.farm.location.api;

import com.poultryflow.farm.location.FarmLocationService;
import com.poultryflow.farm.location.FarmLocationView;
import com.poultryflow.shared.api.ApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiContract.BUSINESS_API_BASE_PATH + "/farms/current/locations")
public class FarmLocationController {

    private static final String ADMIN_AUTHORIZATION =
            "@farmAuthorization.hasAnyCurrentFarmRole(authentication, 'OWNER', 'MANAGER')";

    private final FarmLocationService service;

    public FarmLocationController(FarmLocationService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@farmAuthorization.hasCurrentFarmAccess(authentication)"
            + " && (!#includeInactive || " + ADMIN_AUTHORIZATION + ")")
    @Operation(summary = "List current farm locations")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current farm locations"),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "404", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public List<FarmLocationView> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listCurrent(includeInactive);
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ADMIN_AUTHORIZATION)
    @Operation(summary = "Create a current farm location")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Farm location created"),
        @ApiResponse(responseCode = "400", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "404", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public FarmLocationView create(
            @Valid @RequestBody CreateFarmLocationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.createCurrent(request.toCommand(), jwt.getSubject());
    }

    @PutMapping(
            value = "/{locationId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN_AUTHORIZATION)
    @Operation(summary = "Update a current farm location")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Farm location updated"),
        @ApiResponse(responseCode = "400", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "404", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public FarmLocationView update(
            @PathVariable UUID locationId,
            @Valid @RequestBody UpdateFarmLocationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.updateCurrent(
                locationId,
                request.version(),
                request.toCommand(),
                jwt.getSubject());
    }

    @PutMapping(
            value = "/{locationId}/status",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN_AUTHORIZATION)
    @Operation(summary = "Activate or deactivate a current farm location")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Farm location status updated"),
        @ApiResponse(responseCode = "400", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "404", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public FarmLocationView updateStatus(
            @PathVariable UUID locationId,
            @Valid @RequestBody UpdateFarmLocationStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.changeCurrentStatus(
                locationId,
                request.version(),
                request.status(),
                jwt.getSubject());
    }
}
