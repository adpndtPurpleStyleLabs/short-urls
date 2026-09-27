package com.preonsurl.apis.link.repository;

import com.preonsurl.apis.link.entity.LinkRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LinkRecipientRepository extends JpaRepository<LinkRecipient, Long> {

    List<LinkRecipient> findByShortUrlIdOrderByIdAsc(Long shortUrlId);

    Optional<LinkRecipient> findByTrackingToken(String trackingToken);

    Optional<LinkRecipient> findByShortUrlIdAndEmailIgnoreCase(Long shortUrlId, String email);

    void deleteByShortUrlId(Long shortUrlId);
}
