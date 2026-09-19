package com.preonsurl.apis.repository;

import com.preonsurl.apis.entity.ShortUrlTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShortUrlTagRepository extends JpaRepository<ShortUrlTag, Long> {

    List<ShortUrlTag> findByUrlId(Long urlId);

    List<ShortUrlTag> findByUserId(Long userId);

    List<ShortUrlTag> findByTag(String tag);

    List<ShortUrlTag> findByUserIdAndTag(Long userId, String tag);

    boolean existsByUrlIdAndTag(Long urlId, String tag);

    void deleteByUrlId(Long urlId);
}
