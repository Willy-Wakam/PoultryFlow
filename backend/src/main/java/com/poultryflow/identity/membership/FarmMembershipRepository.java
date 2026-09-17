package com.poultryflow.identity.membership;

import com.poultryflow.identity.access.PoultryFlowRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FarmMembershipRepository extends JpaRepository<FarmMembership, UUID> {

    long countByFarmId(UUID farmId);

    Optional<FarmMembership> findByFarmIdAndKeycloakSubject(UUID farmId, String subject);

    Optional<FarmMembership> findByFarmIdAndNormalizedEmail(UUID farmId, String normalizedEmail);

    Optional<FarmMembership> findByIdAndFarmId(UUID id, UUID farmId);

    List<FarmMembership> findAllByFarmIdOrderByNormalizedEmailAsc(UUID farmId);

    @Query("""
            select count(distinct membership)
            from FarmMembership membership
            join membership.roles role
            where membership.farmId = :farmId
              and membership.status = :status
              and role = :role
            """)
    long countActiveOwners(
            @Param("farmId") UUID farmId,
            @Param("status") FarmMembershipStatus status,
            @Param("role") PoultryFlowRole role);
}
