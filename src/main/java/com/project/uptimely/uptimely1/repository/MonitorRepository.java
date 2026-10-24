package com.project.uptimely.uptimely1.repository;

import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.entity.MonitorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface MonitorRepository extends JpaRepository<Monitor, Long>, JpaSpecificationExecutor<Monitor> {
    @Modifying
    @Transactional
    @Query("update Monitor m set m.currentStatus = :status, m.lastCheckedAt = :checkedAt where m.id = :id")
    int updateStatus(@Param("id") Long id,
                     @Param("status") MonitorStatus status,
                     @Param("checkedAt") Instant checkedAt);

    List<Monitor> findByActiveTrue();

}
