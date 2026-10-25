package com.project.uptimely.uptimely1.repository;

import com.project.uptimely.uptimely1.entity.CheckResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface CheckResultRepository extends JpaRepository<CheckResult,Long>, JpaSpecificationExecutor<CheckResult> {
    Page<CheckResult> findByMonitorIdAndCheckedAtBetween(Long monitorId, Instant from, Instant to, Pageable pageable);

    @Query("""
          select count(c) as total,
                 sum(case when c.up = false then 1 else 0 end) as failed,
                 avg(case when c.up = true then c.responseTimeMs end) as avgResponseMs
          from CheckResult c
          where c.monitor.id = :monitorId and c.checkedAt >= :from
          """)
    CheckStats getStats(@Param("monitorId") Long monitorId, @Param("from") Instant from);
}
