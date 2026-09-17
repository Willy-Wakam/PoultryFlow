package com.poultryflow.identity.membership;

import com.poultryflow.identity.access.PoultryFlowRole;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "farm_memberships")
class FarmMembership {

    @Id
    private UUID id;

    @Column(name = "farm_id", nullable = false, updatable = false)
    private UUID farmId;

    @Column(name = "keycloak_subject", length = 255)
    private String keycloakSubject;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "farm_membership_roles",
            joinColumns = @JoinColumn(name = "membership_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 24)
    private Set<PoultryFlowRole> roles = EnumSet.noneOf(PoultryFlowRole.class);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FarmMembershipStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FarmMembership() {
    }

    static FarmMembership activeOwner(
            UUID id,
            UUID farmId,
            String subject,
            String normalizedEmail,
            Instant now) {
        return create(
                id,
                farmId,
                subject,
                normalizedEmail,
                Set.of(PoultryFlowRole.OWNER),
                FarmMembershipStatus.ACTIVE,
                now);
    }

    static FarmMembership invitation(
            UUID id,
            UUID farmId,
            String normalizedEmail,
            Set<PoultryFlowRole> roles,
            Instant now) {
        return create(
                id,
                farmId,
                null,
                normalizedEmail,
                roles,
                FarmMembershipStatus.INVITED,
                now);
    }

    private static FarmMembership create(
            UUID id,
            UUID farmId,
            String subject,
            String normalizedEmail,
            Set<PoultryFlowRole> roles,
            FarmMembershipStatus status,
            Instant now) {
        FarmMembership membership = new FarmMembership();
        membership.id = id;
        membership.farmId = farmId;
        membership.keycloakSubject = subject;
        membership.normalizedEmail = normalizedEmail;
        membership.roles = copyRoles(roles);
        membership.status = status;
        membership.createdAt = now;
        membership.updatedAt = now;
        return membership;
    }

    boolean claim(String subject, Instant now) {
        if (status != FarmMembershipStatus.INVITED || keycloakSubject != null) {
            return false;
        }
        keycloakSubject = subject;
        status = FarmMembershipStatus.ACTIVE;
        updatedAt = now;
        return true;
    }

    boolean changeRoles(Set<PoultryFlowRole> replacement, Instant now) {
        Set<PoultryFlowRole> canonicalRoles = copyRoles(replacement);
        if (roles.equals(canonicalRoles)) {
            return false;
        }
        roles = canonicalRoles;
        updatedAt = now;
        return true;
    }

    boolean disable(Instant now) {
        if (status == FarmMembershipStatus.DISABLED) {
            return false;
        }
        status = FarmMembershipStatus.DISABLED;
        updatedAt = now;
        return true;
    }

    boolean reEnable(Instant now) {
        if (status != FarmMembershipStatus.DISABLED) {
            return false;
        }
        status = keycloakSubject == null
                ? FarmMembershipStatus.INVITED
                : FarmMembershipStatus.ACTIVE;
        updatedAt = now;
        return true;
    }

    boolean isActiveOwner() {
        return status == FarmMembershipStatus.ACTIVE && roles.contains(PoultryFlowRole.OWNER);
    }

    boolean isActive() {
        return status == FarmMembershipStatus.ACTIVE;
    }

    boolean hasRole(PoultryFlowRole role) {
        return roles.contains(role);
    }

    UUID id() {
        return id;
    }

    String subject() {
        return keycloakSubject;
    }

    FarmMembershipStatus status() {
        return status;
    }

    FarmMembershipView toView() {
        return new FarmMembershipView(
                id,
                farmId,
                normalizedEmail,
                sortedRoles(),
                status,
                version,
                createdAt,
                updatedAt);
    }

    FarmAccessView toAccessView() {
        return new FarmAccessView(
                farmId,
                id,
                sortedRoles(),
                status,
                false);
    }

    private List<PoultryFlowRole> sortedRoles() {
        return roles.stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private static Set<PoultryFlowRole> copyRoles(Set<PoultryFlowRole> source) {
        if (source.isEmpty()) {
            return EnumSet.noneOf(PoultryFlowRole.class);
        }
        return EnumSet.copyOf(source);
    }
}
