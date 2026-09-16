package com.preonsurl.apis.repository;

import com.preonsurl.apis.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findFirstByOriginalUrlAndDirType(String originalUrl, String dirType);

    Optional<ShortUrl> findFirstByOriginalUrlAndDirTypeIsNull(String originalUrl);

    Optional<ShortUrl> findByShortCode(String shortCode);

    Optional<ShortUrl> findByDirTypeAndShortCode(String dirType, String shortCode);

    boolean existsByShortCode(String shortCode);

    @Modifying
    @Query("UPDATE ShortUrl s SET s.clickCount = s.clickCount + 1, s.updatedAt = CURRENT_TIMESTAMP WHERE s.id = :id")
    void incrementClickCount(@Param("id") Long id);
}
