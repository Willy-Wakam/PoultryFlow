package com.poultryflow.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}
