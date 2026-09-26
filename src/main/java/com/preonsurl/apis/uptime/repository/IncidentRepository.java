package com.preonsurl.apis.uptime.repository;

import com.preonsurl.apis.uptime.entity.IncidentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<IncidentRecord, Long> {

    List<IncidentRecord> findAllByOrderByCreatedAtDesc();

    List<IncidentRecord> findTop20ByOrderByCreatedAtDesc();

    long countByCreatedAtAfter(Instant after);

    long countByStatusNotIgnoreCase(String status);
}
