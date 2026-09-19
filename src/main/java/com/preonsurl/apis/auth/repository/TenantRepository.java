package com.preonsurl.apis.auth.repository;

import com.preonsurl.apis.auth.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
}