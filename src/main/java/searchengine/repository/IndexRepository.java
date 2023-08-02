package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    @Query(value = "SELECT `path` FROM `index` join lemma on +" +
            "`index`.lemma_id = lemma.id join `page` on page_id = + " +
            "`page`.id  where lemma_id = :lemmaId",
            nativeQuery = true)
    List<String> findPathsByLemmaId(@Param("lemmaId") int lemmaId);

    @Query(value = "SELECT count(*) FROM `index` where lemma_id = :lemmaId",
            nativeQuery = true)
    Integer findCountIndexByLemmaId(@Param("lemmaId") int lemmaId);

    @Query(value = "SELECT count(*) FROM `index` where lemma_id = :lemmaId and page_id = :pageId",
            nativeQuery = true)
    Integer findCountIndexByLemmaIdAndPageId(@Param("lemmaId") int lemmaId, @Param("pageId") int pageId);

    @Query(value = "SELECT `rank` FROM `index` join lemma  on `index`.lemma_id = lemma.id" +
            " join `page` on page_id = `page`.id  WHERE lemma = :lemma" +
            " AND path = :path AND page.site_id = :siteId",
            nativeQuery = true)
    Integer findRankByLemmaIdAndPathAndSiteId(@Param("lemma") String lemma, @Param("path") String path, @Param("siteId") int siteId);

    @Query(value = "SELECT * FROM `index` WHERE page_id = :pageId",
            nativeQuery = true)
    List<IndexEntity> findByPageEntity(@Param("pageId") int pageId);

    @Query(value = "SELECT count(*) from `index` join lemma on `index`.lemma_id = lemma.id WHERE + " +
            "site_id = :siteId",
            nativeQuery = true)
    Integer findCountIndexBySiteId(@Param("siteId") int siteId);
}
