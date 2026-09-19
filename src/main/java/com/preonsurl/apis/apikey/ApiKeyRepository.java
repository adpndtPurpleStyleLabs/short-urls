package com.preonsurl.apis.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByApiKeyHashAndActiveTrue(String apiKeyHash);

    Optional<ApiKey> findByUserIdAndActiveTrue(Long userId);

    List<ApiKey> findAllByUserIdAndActiveTrue(Long userId);

    List<ApiKey> findAllByUserId(Long userId);

    Optional<ApiKey> findByIdAndUserId(Long id, Long userId);
}