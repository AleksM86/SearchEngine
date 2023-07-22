package searchengine.springDataRepositorys;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface PageJpaRepository extends JpaRepository<PageEntity, Integer> {

    List<PageEntity> findBySiteEntity(SiteEntity siteEntity);
    @Query(
            value = "SELECT count(*) FROM search_engine.page where path = ?1 and site_id = ?2",
            nativeQuery = true)
    Integer findCountByPathAndSiteId(String path , int siteId);
    @Query(
            value = "SELECT * FROM search_engine.page where path = ?1 and site_id = ?2",
            nativeQuery = true)
    List<PageEntity> findPageEntityListByPathAndSiteId(String path , int siteId);
    @Query(
            value = "SELECT count(*) FROM search_engine.page where site_id = :siteId",
            nativeQuery = true)
    Integer findCountBySiteId(@Param("siteId") int siteId);
    @Query(
            value = "SELECT id FROM search_engine.page where path = :path and site_id = :siteId",
            nativeQuery = true)
    Integer findPageIdCountByPathAndSiteId(@Param("path") String path, @Param("siteId") int siteId);

    @Query(
            value = "SELECT content FROM search_engine.page where path = :path and site_id = :siteId",
            nativeQuery = true)
    String findContentByPathAndSiteId(@Param("path") String path, @Param("siteId") int siteId);

    @Query(value = "SELECT * FROM page join site on page.site_id = site.id" +
            " WHERE path = :path AND site.url = :url",
            nativeQuery = true)
    List<PageEntity> findPageEntityListByPathIdAndSiteUrl(@Param("path") String path, @Param("url") String url);
}
