package com.preonsurl.apis.link.repository;

import com.preonsurl.apis.link.entity.NewUrlTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<NewUrlTag> findAllByUrlIdIn(List<Long> urlIds);

    @Query("SELECT DISTINCT t.tag FROM NewUrlTag t WHERE t.userId = :userId ORDER BY t.tag ASC")
    List<String> findDistinctTagsByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT t.urlId FROM NewUrlTag t WHERE t.userId = :userId AND LOWER(t.tag) IN :tags")
    List<Long> findUrlIdsByUserIdAndTagsIn(@Param("userId") Long userId, @Param("tags") List<String> tags);
}
