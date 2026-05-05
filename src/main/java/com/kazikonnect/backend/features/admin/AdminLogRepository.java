package com.kazikonnect.backend.features.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AdminLogRepository extends JpaRepository<AdminLog, UUID> {
    List<AdminLog> findAllByAdminId(UUID adminId);
    List<AdminLog> findAllByTargetId(UUID targetId);
}
