package com.preonsurl.apis.link.repository;

import com.preonsurl.apis.link.entity.NewUrlChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewUrlChangeLogRepository extends JpaRepository<NewUrlChangeLog, Long> {

    List<NewUrlChangeLog> findByUrlIdOrderByCreatedAtDesc(Long urlId);

    List<NewUrlChangeLog> findByUrlIdAndActionOrderByCreatedAtDesc(Long urlId, String action);
}
