package com.preonsurl.apis.domain.repository;

import com.preonsurl.apis.domain.entity.CustomDomain;
import com.preonsurl.apis.domain.entity.DomainStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomDomainRepository extends JpaRepository<CustomDomain, Long> {

    Page<CustomDomain> findByUserId(Long userId, Pageable pageable);

    List<CustomDomain> findByUserIdAndStatus(Long userId, DomainStatus status);

    Optional<CustomDomain> findByIdAndUserId(Long id, Long userId);

    Optional<CustomDomain> findByDomain(String domain);

    boolean existsByDomain(String domain);

    long countByUserId(Long userId);
}
