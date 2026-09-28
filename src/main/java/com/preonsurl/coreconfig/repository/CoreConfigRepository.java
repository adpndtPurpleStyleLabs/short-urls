package com.preonsurl.coreconfig.repository;

import com.preonsurl.coreconfig.entity.CoreConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoreConfigRepository  extends JpaRepository<CoreConfig, Long> {
    Optional<CoreConfig> findByConfigKeyAndActiveTrue(String configKey);
    List<CoreConfig> findAllByActiveTrue();
    List<CoreConfig> findAllByUpdatedAtAfterAndActiveTrue(LocalDateTime updatedAt);
}
