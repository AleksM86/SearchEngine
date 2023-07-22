package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.interfases.services.StatisticsService;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.springDataRepositorys.IndexJpaRepository;
import searchengine.springDataRepositorys.PageJpaRepository;
import searchengine.springDataRepositorys.SiteJpaRepository;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
@Setter
@Getter
public class StatisticsServiceImpl implements StatisticsService {
    @Autowired
    private SiteJpaRepository siteJpaRepository;
    @Autowired
    private PageJpaRepository pageJpaRepository;
    @Autowired
    private IndexJpaRepository indexJpaRepository;
    private static TotalStatistics total = new TotalStatistics();
    private  List<DetailedStatisticsItem> detailed = new ArrayList<>();
    private StatisticsResponse response = new StatisticsResponse();
    private StatisticsData data = new StatisticsData();

    @Override
    public StatisticsResponse getStatistics() {
        detailed.clear();
        List<SiteEntity> siteEntityList = siteJpaRepository.findAll();
        total.setSites(siteEntityList.size());
        data.setTotal(total);
        for (SiteEntity siteEntity : siteEntityList){
            createDetailedStatisticsItem(siteEntity);
        }
        total.setPages(foundPageTotalCount());
        total.setLemmas(foundLemmaTotalCount());
        data.setDetailed(detailed);
        response.setStatistics(data);
        return response;
    }
    private int foundPageTotalCount(){
        int totalPageCount = 0;
        for (DetailedStatisticsItem item : detailed){
            totalPageCount = totalPageCount + item.getPages();
        }
        return totalPageCount;
    }
    private int foundLemmaTotalCount(){
        int totalLemmaCount = 0;
        for (DetailedStatisticsItem item : detailed){
            totalLemmaCount = totalLemmaCount + item.getLemmas();
        }
        return totalLemmaCount;
    }
    private void createDetailedStatisticsItem(SiteEntity siteEntity){
        DetailedStatisticsItem detailedStatisticsItem = new DetailedStatisticsItem();
        detailedStatisticsItem.setStatus(siteEntity.getSiteStatus().toString());
        detailedStatisticsItem.setUrl(siteEntity.getUrl());
        detailedStatisticsItem.setName(siteEntity.getName());
        detailedStatisticsItem.setStatusTime(siteEntity.getStatusTime());
        if (siteEntity.getLastError() == null) {
            detailedStatisticsItem.setError("");
        } else {
            detailedStatisticsItem.setError(siteEntity.getLastError());
        }
        List<PageEntity> pageEntityList = pageJpaRepository.findBySiteEntity(siteEntity);
        int pageCount = pageEntityList.size();
        detailedStatisticsItem.setPages(pageCount);
        int lemmaCount = indexJpaRepository.findCountIndexBySiteId(siteEntity.getId());
        detailedStatisticsItem.setLemmas(lemmaCount);
        detailed.add(detailedStatisticsItem);
    }
    public static TotalStatistics getTotal() {
        return total;
    }
}
