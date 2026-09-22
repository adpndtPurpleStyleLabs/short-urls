package com.preonsurl.apis.analytics.repository;

import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalyticsRepository extends JpaRepository<NewUrlAccessLog, Long> {

    @Query("""
            SELECT CAST(l.accessedAt AS LocalDate), COUNT(l)
            FROM NewUrlAccessLog l, NewUrl u
            WHERE l.shortUrlId = u.id
              AND u.userId = :userId
              AND l.accessedAt >= :startTime
              AND l.accessedAt < :endTime
            GROUP BY CAST(l.accessedAt AS LocalDate)
            ORDER BY CAST(l.accessedAt AS LocalDate) ASC
            """)
    List<Object[]> getDailyClicksByUser(
            @Param("userId") Long userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("""
            SELECT CAST(l.accessedAt AS LocalDate), COUNT(l)
            FROM NewUrlAccessLog l
            WHERE l.shortUrlId = :shortUrlId
              AND l.accessedAt >= :startTime
              AND l.accessedAt < :endTime
            GROUP BY CAST(l.accessedAt AS LocalDate)
            ORDER BY CAST(l.accessedAt AS LocalDate) ASC
            """)
    List<Object[]> getDailyClicksByShortUrlId(
            @Param("shortUrlId") Long shortUrlId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
