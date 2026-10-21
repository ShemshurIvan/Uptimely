package com.project.uptimely.uptimely1.repository;

import com.project.uptimely.uptimely1.entity.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CheckResultRepository extends JpaRepository<CheckResult,Long>, JpaSpecificationExecutor<CheckResult> {
}
