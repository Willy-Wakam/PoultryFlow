package com.poultryflow.farm;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FarmRepository extends JpaRepository<Farm, UUID> {

    List<Farm> findTop2ByOrderByCreatedAtAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select farm from Farm farm where farm.id = :id")
    Optional<Farm> findByIdForUpdate(@Param("id") UUID id);
}
