package com.preonsurl.apis.link.repository;

import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewUrlAccessLogRepository extends JpaRepository<NewUrlAccessLog, Long> {

    List<NewUrlAccessLog> findByShortUrlIdOrderByAccessedAtDesc(Long shortUrlId);

    List<NewUrlAccessLog> findByShortCodeOrderByAccessedAtDesc(String shortCode);

    long countByShortUrlId(Long shortUrlId);

    long countByShortCode(String shortCode);
}
