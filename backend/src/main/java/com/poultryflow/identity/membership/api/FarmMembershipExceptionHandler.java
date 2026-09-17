package com.poultryflow.identity.membership.api;

import com.poultryflow.farm.AmbiguousFarmProfileException;
import com.poultryflow.farm.FarmProfileNotConfiguredException;
import com.poultryflow.identity.membership.BootstrapEmailRequiredException;
import com.poultryflow.identity.membership.DuplicateFarmMembershipException;
import com.poultryflow.identity.membership.FarmMembershipAccessDeniedException;
import com.poultryflow.identity.membership.FarmMembershipNotFoundException;
import com.poultryflow.identity.membership.InvalidMembershipRolesException;
import com.poultryflow.identity.membership.LastActiveOwnerException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FarmMembershipController.class)
public class FarmMembershipExceptionHandler {

    @ExceptionHandler(FarmMembershipAccessDeniedException.class)
    ResponseEntity<ProblemDetail> accessDenied(HttpServletRequest request) {
        return problem(
                HttpStatus.FORBIDDEN,
                "Authorization denied",
                "You do not have active membership access to this farm.",
                "AUTHORIZATION_DENIED",
                "authorization-denied",
                request);
    }

    @ExceptionHandler(FarmProfileNotConfiguredException.class)
    ResponseEntity<ProblemDetail> farmNotConfigured(HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Farm profile not configured",
                "The farm profile must be configured before users can be managed.",
                "FARM_PROFILE_NOT_CONFIGURED",
                "farm-profile-not-configured",
                request);
    }

    @ExceptionHandler(FarmMembershipNotFoundException.class)
    ResponseEntity<ProblemDetail> membershipNotFound(HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Membership not found",
                "The requested farm membership was not found.",
                "FARM_MEMBERSHIP_NOT_FOUND",
                "farm-membership-not-found",
                request);
    }

    @ExceptionHandler(InvalidMembershipRolesException.class)
    ResponseEntity<ProblemDetail> invalidRoles(HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid membership roles",
                "At least one supported farm role is required.",
                "INVALID_MEMBERSHIP_ROLES",
                "invalid-membership-roles",
                request);
    }

    @ExceptionHandler(DuplicateFarmMembershipException.class)
    ResponseEntity<ProblemDetail> duplicateMembership(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Membership already exists",
                "A membership or invitation already exists for that email address.",
                "FARM_MEMBERSHIP_ALREADY_EXISTS",
                "farm-membership-already-exists",
                request);
    }

    @ExceptionHandler(LastActiveOwnerException.class)
    ResponseEntity<ProblemDetail> lastOwner(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Active owner required",
                "At least one active farm owner must remain.",
                "LAST_ACTIVE_OWNER_REQUIRED",
                "last-active-owner-required",
                request);
    }

    @ExceptionHandler(BootstrapEmailRequiredException.class)
    ResponseEntity<ProblemDetail> bootstrapEmailRequired(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Identity email required",
                "The bootstrap owner must have an email address before users can be managed.",
                "BOOTSTRAP_EMAIL_REQUIRED",
                "bootstrap-email-required",
                request);
    }

    @ExceptionHandler(AmbiguousFarmProfileException.class)
    ResponseEntity<ProblemDetail> ambiguousFarm(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Current farm is ambiguous",
                "The current farm cannot be resolved safely.",
                "FARM_PROFILE_AMBIGUOUS",
                "farm-profile-ambiguous",
                request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> concurrentModification(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "The membership changed while this request was being processed. Refresh and retry.",
                "CONCURRENT_MODIFICATION",
                "concurrent-modification",
                request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            String code,
            String type,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:poultryflow:problem:" + type));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
