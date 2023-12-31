package searchengine.services;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.*;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Data
public class StatisticsServiceImpl implements StatisticsService {
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private IndexRepository indexRepository;
    private TotalStatistics total = new TotalStatistics();
    private List<DetailedStatisticsItem> detailed = new ArrayList<>();
    private StatisticsResponse response = new StatisticsResponse();
    private StatisticsData data = new StatisticsData();

    @Override
    public StatisticsResponse getStatistics() {
        detailed.clear();
        List<SiteEntity> siteEntityList = siteRepository.findAll();
        total.setSites(siteEntityList.size());
        total.setIndexing(checkIsIndexing());
        data.setTotal(total);
        for (SiteEntity siteEntity : siteEntityList) {
            createDetailedStatisticsItem(siteEntity);
        }
        total.setPages(foundPageTotalCount());
        total.setLemmas(foundLemmaTotalCount());
        data.setDetailed(detailed);
        response.setStatistics(data);
        return response;
    }

    private int foundPageTotalCount() {
        int totalPageCount = 0;
        for (DetailedStatisticsItem item : detailed) {
            totalPageCount = totalPageCount + item.getPages();
        }
        return totalPageCount;
    }

    private int foundLemmaTotalCount() {
        int totalLemmaCount = 0;
        for (DetailedStatisticsItem item : detailed) {
            totalLemmaCount = totalLemmaCount + item.getLemmas();
        }
        return totalLemmaCount;
    }

    private void createDetailedStatisticsItem(SiteEntity siteEntity) {
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
        List<PageEntity> pageEntityList = pageRepository.findBySiteEntity(siteEntity);
        int pageCount = pageEntityList.size();
        detailedStatisticsItem.setPages(pageCount);
        int lemmaCount = indexRepository.findCountIndexBySiteId(siteEntity.getId());
        detailedStatisticsItem.setLemmas(lemmaCount);
        detailed.add(detailedStatisticsItem);
    }

    private boolean checkIsIndexing() {
        List<SiteEntity> siteEntityList = siteRepository.findAll();
        if (siteEntityList.isEmpty()) {
            return false;
        }
        for (SiteEntity siteEntity : siteEntityList) {
            if (siteEntity.getSiteStatus().equals(SiteStatus.INDEXING)) {
                return true;
            }
        }
        return false;
    }
}
