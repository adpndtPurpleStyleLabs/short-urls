package com.preonsurl.apis.link.repository;

import com.preonsurl.apis.link.entity.UsagePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsagePolicyRepository extends JpaRepository<UsagePolicy, Long> {

    Optional<UsagePolicy> findByShortUrlId(Long shortUrlId);

    void deleteByShortUrlId(Long shortUrlId);

    @Modifying
    @Query("UPDATE UsagePolicy u SET u.currentUsage = u.currentUsage + 1, u.updatedAt = CURRENT_INSTANT WHERE u.shortUrlId = :shortUrlId")
    int incrementUsage(@Param("shortUrlId") Long shortUrlId);
}

