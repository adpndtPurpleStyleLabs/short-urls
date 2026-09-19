package com.preonsurl.apis.auth.repository;

import com.preonsurl.apis.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTenantIdAndUsername(Long tenantId, String username);
    boolean existsByTenantIdAndUsername(Long tenantId, String username);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}