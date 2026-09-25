package com.preonsurl.apis.publiclink.repository;

import com.preonsurl.apis.publiclink.entity.PublicSecureUrlAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicSecureUrlAccessLogRepository extends JpaRepository<PublicSecureUrlAccessLog, Long> {

    List<PublicSecureUrlAccessLog> findByShortKey(String shortKey);

    List<PublicSecureUrlAccessLog> findByPublicSecureUrlId(Long publicSecureUrlId);
}
