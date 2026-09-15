package com.poultryflow.farm;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmRepository extends JpaRepository<Farm, UUID> {

    List<Farm> findTop2ByOrderByCreatedAtAscIdAsc();
}
