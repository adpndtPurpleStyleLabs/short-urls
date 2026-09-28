package com.preonsurl.apis.publiclink.repository;

import com.preonsurl.apis.publiclink.entity.PublicSecureUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PublicSecureUrlRepository extends JpaRepository<PublicSecureUrl, Long> {

    Optional<PublicSecureUrl> findByShortKeyAndIsActiveTrue(String shortKey);

    Optional<PublicSecureUrl> findByPsecureUrlAndIsActiveTrue(String psecureUrl);

    boolean existsByShortKey(String shortKey);

    @Modifying
    @Query("UPDATE PublicSecureUrl p SET p.clickCount = p.clickCount + 1 WHERE p.id = :id")
    void incrementClickCount(@Param("id") Long id);


    @Query("""
            SELECT p
            FROM PublicSecureUrl p
            WHERE p.isActive = true
              AND p.createdAt < :cutoff
            """)
    List<PublicSecureUrl> findActiveUrlsOlderThan(@Param("cutoff") Instant cutoff);
}
