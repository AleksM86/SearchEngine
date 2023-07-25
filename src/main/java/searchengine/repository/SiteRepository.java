package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {
    List<SiteEntity> findByUrl(@Param("url") String url);
    @Query(
            value = "SELECT id FROM search_engine.site where url = :url",
            nativeQuery = true)
    Integer findCountByPathAndSiteId(@Param("url") String url);

   }
