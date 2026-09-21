package com.preonsurl.apis.link.repository;

import com.preonsurl.apis.link.entity.AccessPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, Long> {

    Optional<AccessPolicy> findByShortUrlId(Long shortUrlId);

    void deleteByShortUrlId(Long shortUrlId);
}
