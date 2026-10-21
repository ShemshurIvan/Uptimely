package com.project.uptimely.uptimely1.repository;

import com.project.uptimely.uptimely1.entity.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {
}
