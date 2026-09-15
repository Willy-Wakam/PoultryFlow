package com.poultryflow.farm.api;

import com.poultryflow.farm.FarmProfileService;
import com.poultryflow.farm.FarmProfileView;
import com.poultryflow.shared.api.ApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiContract.BUSINESS_API_BASE_PATH + "/farms/current")
@PreAuthorize("hasRole('OWNER')")
public class FarmProfileController {

    private final FarmProfileService service;

    public FarmProfileController(FarmProfileService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the current farm profile")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Current farm profile",
                content = @Content(schema = @Schema(implementation = FarmProfileView.class))),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "404", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public FarmProfileView getCurrent() {
        return service.getCurrent();
    }

    @PutMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or update the current farm profile")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Persisted farm profile",
                content = @Content(schema = @Schema(implementation = FarmProfileView.class))),
        @ApiResponse(responseCode = "400", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "401", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "403", ref = ApiContract.PROBLEM_RESPONSE_REF),
        @ApiResponse(responseCode = "409", ref = ApiContract.PROBLEM_RESPONSE_REF)
    })
    public FarmProfileView saveCurrent(
            @Valid @RequestBody FarmProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.saveCurrent(request.toCommand(), jwt.getSubject());
    }
}
