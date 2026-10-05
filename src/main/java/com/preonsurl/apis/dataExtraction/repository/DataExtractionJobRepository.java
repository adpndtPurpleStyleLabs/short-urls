package com.preonsurl.apis.dataExtraction.repository;

import com.preonsurl.apis.dataExtraction.entity.DataExtractionJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataExtractionJobRepository extends JpaRepository<DataExtractionJob, Long> {

    Page<DataExtractionJob> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<DataExtractionJob> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<DataExtractionJob> findByPublicIdAndUserId(String publicId, Long userId);

    Optional<DataExtractionJob> findByPublicId(String publicId);
}
