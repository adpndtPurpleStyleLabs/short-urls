package com.preonsurl.apis.link.repository;

import com.preonsurl.apis.link.entity.NewUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewUrlRepository extends JpaRepository<NewUrl, Long> {

    Optional<NewUrl> findFirstByOriginalUrl(String originalUrl);

    Optional<NewUrl> findFirstByOriginalUrlAndCustomPathIsNull(String originalUrl);

    List<NewUrl> findAllByOriginalUrlAndCustomPathIsNullOrderByIdDesc(String originalUrl);

    List<NewUrl> findAllByOriginalUrlAndCustomPathOrderByIdDesc(String originalUrl, String customPath);

    Optional<NewUrl> findFirstByOriginalUrlAndUserId(String originalUrl, Long userId);

    Optional<NewUrl> findByNewUrl(String newUrl);

    Optional<NewUrl> findByNewUrlAndUserId(String newUrl, Long userId);

    Optional<NewUrl> findByShortCode(String shortCode);

    Optional<NewUrl> findByShortCodeAndUserId(String shortCode, Long userId);

    boolean existsByShortCode(String shortCode);

    @Modifying
    @Query("UPDATE NewUrl s SET s.clickCount = s.clickCount + 1, s.updatedAt = CURRENT_INSTANT WHERE s.id = :id")
    void incrementClickCount(@Param("id") Long id);
}
