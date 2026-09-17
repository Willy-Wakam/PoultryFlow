package com.poultryflow.identity.membership;

import com.poultryflow.audit.AuditAction;
import com.poultryflow.audit.AuditEventAppender;
import com.poultryflow.farm.AmbiguousFarmProfileException;
import com.poultryflow.farm.CurrentFarmProvider;
import com.poultryflow.identity.access.PoultryFlowRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmMembershipService {

    private static final String MEMBERSHIP_ENTITY_TYPE = "FARM_MEMBERSHIP";
    private static final List<String> BOOTSTRAP_FIELDS = List.of(
            "keycloakSubject", "normalizedEmail", "roles", "status");
    private static final List<String> INVITATION_FIELDS = List.of(
            "normalizedEmail", "roles", "status");
    private static final List<String> CLAIM_FIELDS = List.of("keycloakSubject", "status");
    private static final List<String> ROLE_FIELDS = List.of("roles");
    private static final List<String> STATUS_FIELDS = List.of("status");

    private final FarmMembershipRepository repository;
    private final CurrentFarmProvider currentFarmProvider;
    private final AuditEventAppender auditEventAppender;

    public FarmMembershipService(
            FarmMembershipRepository repository,
            CurrentFarmProvider currentFarmProvider,
            AuditEventAppender auditEventAppender) {
        this.repository = repository;
        this.currentFarmProvider = currentFarmProvider;
        this.auditEventAppender = auditEventAppender;
    }

    @Transactional
    public FarmAccessView currentAccess(Authentication authentication) {
        return resolveCurrentAccess(actor(authentication));
    }

    @Transactional
    public boolean hasCurrentRole(Authentication authentication, PoultryFlowRole role) {
        return resolveCurrentAccess(actor(authentication)).roles().contains(role);
    }

    @Transactional
    public boolean hasAnyCurrentRole(
            Authentication authentication,
            Set<PoultryFlowRole> roles) {
        return resolveCurrentAccess(actor(authentication)).roles().stream()
                .anyMatch(roles::contains);
    }

    @Transactional
    public boolean hasCurrentAccess(Authentication authentication) {
        resolveCurrentAccess(actor(authentication));
        return true;
    }

    @Transactional
    public List<FarmMembershipView> list(Authentication authentication) {
        Actor actor = actor(authentication);
        UUID farmId = currentFarmProvider.requireCurrentFarmId();
        requireOwner(actor, farmId, false);
        return repository.findAllByFarmIdOrderByNormalizedEmailAsc(farmId).stream()
                .map(FarmMembership::toView)
                .toList();
    }

    @Transactional
    public FarmMembershipView invite(
            Authentication authentication,
            String rawEmail,
            Set<PoultryFlowRole> rawRoles) {
        Actor actor = actor(authentication);
        UUID farmId = currentFarmProvider.lockCurrentFarmId();
        requireOwner(actor, farmId, true);

        String email = normalizeEmail(rawEmail);
        Set<PoultryFlowRole> roles = requireRoles(rawRoles);
        if (repository.findByFarmIdAndNormalizedEmail(farmId, email).isPresent()) {
            throw new DuplicateFarmMembershipException();
        }

        FarmMembership invitation = repository.saveAndFlush(FarmMembership.invitation(
                UUID.randomUUID(), farmId, email, roles, now()));
        auditEventAppender.append(
                actor.subject(),
                AuditAction.FARM_MEMBERSHIP_INVITED,
                MEMBERSHIP_ENTITY_TYPE,
                invitation.id(),
                INVITATION_FIELDS);
        return invitation.toView();
    }

    @Transactional
    public FarmMembershipView updateRoles(
            Authentication authentication,
            UUID membershipId,
            Set<PoultryFlowRole> rawRoles) {
        Actor actor = actor(authentication);
        UUID farmId = currentFarmProvider.lockCurrentFarmId();
        requireOwner(actor, farmId, true);
        FarmMembership target = membership(farmId, membershipId);
        Set<PoultryFlowRole> roles = requireRoles(rawRoles);

        if (target.isActiveOwner()
                && !roles.contains(PoultryFlowRole.OWNER)
                && activeOwnerCount(farmId) == 1) {
            throw new LastActiveOwnerException();
        }
        if (!target.changeRoles(roles, now())) {
            return target.toView();
        }

        repository.flush();
        auditEventAppender.append(
                actor.subject(),
                AuditAction.FARM_MEMBERSHIP_ROLES_CHANGED,
                MEMBERSHIP_ENTITY_TYPE,
                target.id(),
                ROLE_FIELDS);
        return target.toView();
    }

    @Transactional
    public FarmMembershipView setEnabled(
            Authentication authentication,
            UUID membershipId,
            boolean enabled) {
        Actor actor = actor(authentication);
        UUID farmId = currentFarmProvider.lockCurrentFarmId();
        requireOwner(actor, farmId, true);
        FarmMembership target = membership(farmId, membershipId);

        if (!enabled && target.isActiveOwner() && activeOwnerCount(farmId) == 1) {
            throw new LastActiveOwnerException();
        }

        boolean changed = enabled ? target.reEnable(now()) : target.disable(now());
        if (!changed) {
            return target.toView();
        }

        repository.flush();
        auditEventAppender.append(
                actor.subject(),
                enabled
                        ? AuditAction.FARM_MEMBERSHIP_RE_ENABLED
                        : AuditAction.FARM_MEMBERSHIP_DISABLED,
                MEMBERSHIP_ENTITY_TYPE,
                target.id(),
                STATUS_FIELDS);
        return target.toView();
    }

    private FarmAccessView resolveCurrentAccess(Actor actor) {
        try {
            return currentFarmProvider.findCurrentFarmId()
                    .map(farmId -> resolveAccessForFarm(actor, farmId, false))
                    .orElseGet(() -> bootstrapAuthorityWithoutFarm(actor));
        } catch (AmbiguousFarmProfileException exception) {
            if (actor.globalOwner() && repository.count() == 0) {
                return bootstrapAccess(null);
            }
            throw exception;
        }
    }

    private FarmAccessView resolveAccessForFarm(Actor actor, UUID farmId, boolean farmLocked) {
        var subjectMembership = repository.findByFarmIdAndKeycloakSubject(
                farmId, actor.subject());
        if (subjectMembership.isPresent()) {
            FarmMembership membership = subjectMembership.get();
            if (!membership.isActive()) {
                throw new FarmMembershipAccessDeniedException();
            }
            return membership.toAccessView();
        }

        if (repository.countByFarmId(farmId) == 0 && actor.globalOwner()) {
            return bootstrapAccess(farmId);
        }

        if (actor.emailVerified() && actor.normalizedEmail() != null) {
            if (!farmLocked) {
                currentFarmProvider.lockCurrentFarmId();
                return resolveAccessForFarm(actor, farmId, true);
            }
            var invitation = repository.findByFarmIdAndNormalizedEmail(
                    farmId, actor.normalizedEmail());
            if (invitation.isPresent()) {
                FarmMembership membership = invitation.get();
                if (membership.claim(actor.subject(), now())) {
                    repository.flush();
                    auditEventAppender.append(
                            actor.subject(),
                            AuditAction.FARM_MEMBERSHIP_INVITATION_CLAIMED,
                            MEMBERSHIP_ENTITY_TYPE,
                            membership.id(),
                            CLAIM_FIELDS);
                    return membership.toAccessView();
                }
            }
        }

        throw new FarmMembershipAccessDeniedException();
    }

    private void requireOwner(Actor actor, UUID farmId, boolean allowBootstrap) {
        if (repository.countByFarmId(farmId) == 0) {
            if (!actor.globalOwner()) {
                throw new FarmMembershipAccessDeniedException();
            }
            if (allowBootstrap) {
                bootstrapOwner(actor, farmId);
            }
            return;
        }

        FarmAccessView access = resolveAccessForFarm(actor, farmId, allowBootstrap);
        if (!access.roles().contains(PoultryFlowRole.OWNER)) {
            throw new FarmMembershipAccessDeniedException();
        }
    }

    private void bootstrapOwner(Actor actor, UUID farmId) {
        if (actor.normalizedEmail() == null) {
            throw new BootstrapEmailRequiredException();
        }
        FarmMembership owner = repository.saveAndFlush(FarmMembership.activeOwner(
                UUID.randomUUID(),
                farmId,
                actor.subject(),
                actor.normalizedEmail(),
                now()));
        auditEventAppender.append(
                actor.subject(),
                AuditAction.FARM_MEMBERSHIP_OWNER_BOOTSTRAPPED,
                MEMBERSHIP_ENTITY_TYPE,
                owner.id(),
                BOOTSTRAP_FIELDS);
    }

    private FarmMembership membership(UUID farmId, UUID membershipId) {
        return repository.findByIdAndFarmId(membershipId, farmId)
                .orElseThrow(FarmMembershipNotFoundException::new);
    }

    private long activeOwnerCount(UUID farmId) {
        return repository.countActiveOwners(
                farmId, FarmMembershipStatus.ACTIVE, PoultryFlowRole.OWNER);
    }

    private FarmAccessView bootstrapAuthorityWithoutFarm(Actor actor) {
        if (!actor.globalOwner() || repository.count() != 0) {
            throw new FarmMembershipAccessDeniedException();
        }
        return bootstrapAccess(null);
    }

    private FarmAccessView bootstrapAccess(UUID farmId) {
        return new FarmAccessView(
                farmId,
                null,
                List.of(PoultryFlowRole.OWNER),
                FarmMembershipStatus.ACTIVE,
                true);
    }

    private Set<PoultryFlowRole> requireRoles(Set<PoultryFlowRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new InvalidMembershipRolesException();
        }
        return roles.stream()
                .sorted(Comparator.comparing(Enum::name))
                .collect(() -> EnumSet.noneOf(PoultryFlowRole.class), Set::add, Set::addAll);
    }

    private Actor actor(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new FarmMembershipAccessDeniedException();
        }
        String email = jwt.getClaimAsString("email");
        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaim("email_verified"));
        boolean globalOwner = authentication.getAuthorities().stream()
                .anyMatch(authority -> PoultryFlowRole.OWNER.authority()
                        .equals(authority.getAuthority()));
        return new Actor(
                jwt.getSubject(),
                email == null || email.isBlank() ? null : normalizeEmail(email),
                emailVerified,
                globalOwner);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private record Actor(
            String subject,
            String normalizedEmail,
            boolean emailVerified,
            boolean globalOwner) {
    }
}
