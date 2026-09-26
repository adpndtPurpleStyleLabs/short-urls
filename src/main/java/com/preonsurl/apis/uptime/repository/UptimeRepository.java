package com.preonsurl.apis.uptime.repository;

import com.preonsurl.apis.uptime.entity.UptimeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UptimeRepository extends JpaRepository<UptimeRecord, Long> {

    Optional<UptimeRecord> findTopByOrderByRecordedAtDesc();

    Optional<UptimeRecord> findTopByServiceNameOrderByRecordedAtDesc(String serviceName);

    List<UptimeRecord> findTop100ByOrderByRecordedAtDesc();

    List<UptimeRecord> findByRecordedAtAfterOrderByRecordedAtAsc(Instant after);

    long countByRecordedAtAfterAndStatus(Instant after, String status);

    long countByRecordedAtAfter(Instant after);
}
