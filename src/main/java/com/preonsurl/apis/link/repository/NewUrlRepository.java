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
    Optional<NewUrl> findByPublicIdAndUserId(String publicId, Long userId);
    Optional<NewUrl> findByShortCode(String shortCode);

    Optional<NewUrl> findByShortCodeAndUserId(String shortCode, Long userId);

    Optional<NewUrl> findByCustomPath(String customPath);

    Optional<NewUrl> findByCustomPathAndShortCode(String customPath, String shortCode);

    boolean existsByShortCode(String shortCode);

    org.springframework.data.domain.Page<NewUrl> findAllByUserId(Long userId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT u FROM NewUrl u
            WHERE u.userId = :userId
              AND (
                  LOWER(u.newUrl) LIKE LOWER(CONCAT('%', :likeUrl, '%'))
                  OR LOWER(u.originalUrl) LIKE LOWER(CONCAT('%', :likeUrl, '%'))
                  OR (u.shortCode IS NOT NULL AND LOWER(u.shortCode) LIKE LOWER(CONCAT('%', :likeUrl, '%')))
                  OR (u.customPath IS NOT NULL AND LOWER(u.customPath) LIKE LOWER(CONCAT('%', :likeUrl, '%')))
                  OR (u.note IS NOT NULL AND LOWER(u.note) LIKE LOWER(CONCAT('%', :likeUrl, '%')))
              )
            """)
    org.springframework.data.domain.Page<NewUrl> findAllByUserIdAndLikeUrl(
            @Param("userId") Long userId,
            @Param("likeUrl") String likeUrl,
            org.springframework.data.domain.Pageable pageable
    );

    long countByUserId(Long userId);

    @Query("SELECT COUNT(u) FROM NewUrl u WHERE u.userId = :userId AND u.isActive = true AND (u.expireAt IS NULL OR u.expireAt > :now) AND (u.usageLimit IS NULL OR u.clickCount < u.usageLimit)")
    long countActiveByUserId(@Param("userId") Long userId, @Param("now") java.time.Instant now);

    @Query("""
            SELECT CAST(u.createdAt AS LocalDate), COUNT(u)
            FROM NewUrl u
            WHERE u.userId = :userId
              AND u.createdAt >= :startInstant
              AND u.createdAt < :endInstant
            GROUP BY CAST(u.createdAt AS LocalDate)
            ORDER BY CAST(u.createdAt AS LocalDate) ASC
            """)
    List<Object[]> getDailyCreatedUrlsByUser(
            @Param("userId") Long userId,
            @Param("startInstant") java.time.Instant startInstant,
            @Param("endInstant") java.time.Instant endInstant
    );

    @Modifying
    @Query("UPDATE NewUrl s SET s.clickCount = s.clickCount + 1, s.updatedAt = CURRENT_INSTANT WHERE s.id = :id")
    void incrementClickCount(@Param("id") Long id);

    Optional<NewUrl> findByPublicId(String publicId);

    boolean existsByPublicId(String publicId);

    List<NewUrl> findAllByPublicIdIsNull();
}

