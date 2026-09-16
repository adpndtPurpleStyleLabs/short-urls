package com.preonsurl.apis.repository;

import com.preonsurl.apis.entity.ShortUrlAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShortUrlAccessLogRepository extends JpaRepository<ShortUrlAccessLog, Long> {

    List<ShortUrlAccessLog> findByShortUrlIdOrderByAccessedAtDesc(Long shortUrlId);

    List<ShortUrlAccessLog> findByShortCodeOrderByAccessedAtDesc(String shortCode);

    long countByShortUrlId(Long shortUrlId);

    long countByShortCode(String shortCode);
}
