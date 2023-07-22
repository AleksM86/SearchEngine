package searchengine.springDataRepositorys;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;

import java.util.List;

@Repository
public interface LemmaJpaRepository extends JpaRepository<LemmaEntity, Integer> {

    List<LemmaEntity> findByLemmaAndSiteId(String lemma , int siteId);
}
