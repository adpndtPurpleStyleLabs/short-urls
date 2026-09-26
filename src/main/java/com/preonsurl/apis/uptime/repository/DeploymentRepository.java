package com.preonsurl.apis.uptime.repository;

import com.preonsurl.apis.uptime.entity.DeploymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<DeploymentRecord, Long> {

    Optional<DeploymentRecord> findTopByOrderByDeployedAtDesc();

    List<DeploymentRecord> findAllByOrderByDeployedAtDesc();

    boolean existsByCommitRef(String commitRef);
}
