package com.preonsurl.apis.link.repository;

import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewUrlAccessLogRepository extends JpaRepository<NewUrlAccessLog, Long> {

    List<NewUrlAccessLog> findByShortUrlIdOrderByAccessedAtDesc(Long shortUrlId);

    List<NewUrlAccessLog> findByShortCodeOrderByAccessedAtDesc(String shortCode);

    long countByShortUrlId(Long shortUrlId);

    long countByShortCode(String shortCode);

    Page<NewUrlAccessLog> findByShortUrlId(Long shortUrlId, Pageable pageable);

    @Query(
            value = "SELECT l FROM NewUrlAccessLog l, NewUrl u WHERE l.shortUrlId = u.id AND u.userId = :userId",
            countQuery = "SELECT COUNT(l) FROM NewUrlAccessLog l, NewUrl u WHERE l.shortUrlId = u.id AND u.userId = :userId"
    )
    Page<NewUrlAccessLog> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT l.shortUrlId, MAX(l.accessedAt) FROM NewUrlAccessLog l WHERE l.shortUrlId IN :shortUrlIds GROUP BY l.shortUrlId")
    List<Object[]> findLatestAccessTimesByShortUrlIdIn(@Param("shortUrlIds") List<Long> shortUrlIds);
}

