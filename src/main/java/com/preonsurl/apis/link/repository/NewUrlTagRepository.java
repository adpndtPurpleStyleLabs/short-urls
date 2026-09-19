package com.preonsurl.apis.link.repository;

import com.preonsurl.apis.link.entity.NewUrlTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewUrlTagRepository extends JpaRepository<NewUrlTag, Long> {

    List<NewUrlTag> findByUrlId(Long urlId);

    List<NewUrlTag> findByUserId(Long userId);

    List<NewUrlTag> findByTag(String tag);

    List<NewUrlTag> findByUserIdAndTag(Long userId, String tag);

    boolean existsByUrlIdAndTag(Long urlId, String tag);

    void deleteByUrlId(Long urlId);
}
