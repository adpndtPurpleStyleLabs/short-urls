package com.preonsurl.apis.link.repository;

import com.preonsurl.apis.link.entity.NewUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NewUrlRepository extends JpaRepository<NewUrl, Long> {

    Optional<NewUrl> findFirstByOriginalUrlAndDirType(String originalUrl, String dirType);

    Optional<NewUrl> findFirstByOriginalUrlAndDirTypeIsNull(String originalUrl);

    Optional<NewUrl> findFirstByOriginalUrlAndUserId(String originalUrl, Long userId);

    Optional<NewUrl> findFirstByOriginalUrlAndDirTypeAndUserId(String originalUrl, String dirType, Long userId);

    Optional<NewUrl> findFirstByOriginalUrlAndDirTypeIsNullAndUserId(String originalUrl, Long userId);

    Optional<NewUrl> findByFullShortUrl(String fullShortUrl);

    Optional<NewUrl> findByFullShortUrlAndUserId(String fullShortUrl, Long userId);

    Optional<NewUrl> findByShortCode(String shortCode);

    Optional<NewUrl> findByDirTypeAndShortCode(String dirType, String shortCode);

    boolean existsByShortCode(String shortCode);

    @Modifying
    @Query("UPDATE NewUrl s SET s.clickCount = s.clickCount + 1, s.updatedAt = CURRENT_INSTANT WHERE s.id = :id")
    void incrementClickCount(@Param("id") Long id);
}
