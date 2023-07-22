package searchengine.services;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.interfases.services.IndexingService;
import searchengine.model.SiteEntity;
import searchengine.dto.statistics.SiteStatus;
import searchengine.springDataRepositorys.IndexJpaRepository;
import searchengine.springDataRepositorys.LemmaJpaRepository;
import searchengine.springDataRepositorys.PageJpaRepository;
import searchengine.springDataRepositorys.SiteJpaRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
public class IndexingServiceImpl implements IndexingService {
    @Autowired
    private SiteJpaRepository siteJpaRepository;
    @Autowired
    private PageJpaRepository pageJpaRepository;
    @Autowired
    private LemmaJpaRepository lemmaJpaRepository;
    @Autowired
    private IndexJpaRepository indexJpaRepository;
    private boolean isIndexing = false;
    private final SitesList sites;
    private Set<SiteEntity> siteEntitySet = new HashSet<>();

    public IndexingServiceImpl(SitesList sites) {
        this.sites = sites;
        for (Site site : sites.getSites()) {
            site.setUrl(site.getUrl().replaceFirst("www\\.", ""));
        }
    }

    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse indexingResponse = new IndexingResponse();
        if (isIndexing) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация уже запущенна");
            return indexingResponse;
        }
        isIndexing = true;

        //Очищаем базу данных
        clearAllTables();

        //Отправляем список сайтов на индексацию
        indexing(sites.getSites());
        if (!StatisticsServiceImpl.getTotal().isIndexing()) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Указанные сайты не доступны");
        }
        indexingResponse.setResult(true);
        return indexingResponse;
    }

    private void indexing(List<Site> sites) {
        ParseSiteService.isStartParse();
        //Для каждого сайта создаем сущность, записываем ее в таблицу и отправляем сайт на парсинг
        for (Site site : sites) {
            ForkJoinPool fjp = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatusTime(LocalDateTime.now());
            try {
                Jsoup.connect(site.getUrl()).execute();
                siteEntity.setSiteStatus(SiteStatus.INDEXING);
                siteEntitySet.add(siteEntity);
                StatisticsServiceImpl.getTotal().setIndexing(true);
                ParseSiteService parseSiteService = new ParseSiteService(site.getUrl(), siteEntity, fjp);
                fjp.execute(parseSiteService);
            }
            catch (Exception e) {
                siteEntity.setSiteStatus(SiteStatus.FAILED);
                siteEntity.setLastError("Сайт не доступен");
                siteJpaRepository.save(siteEntity);
                e.printStackTrace();
                continue;
            }
            siteJpaRepository.save(siteEntity);
        }
        if (siteEntitySet.isEmpty()) {
            StatisticsServiceImpl.getTotal().setIndexing(false);
            isIndexing = false;
        }
    }

    public IndexingResponse stopIndexing() {
        IndexingResponse indexingResponse = new IndexingResponse();
        if (isIndexing) {
            //Прекращаем прием задач в пул потоков
            ParseSiteService.isStopParse();
            for (SiteEntity siteEntity : siteEntitySet) {
                siteEntity.setSiteStatus(SiteStatus.INDEXED);
                siteEntity.setLastError("Индексация отстановленна пользователем");
                siteJpaRepository.save(siteEntity);
            }
            StatisticsServiceImpl.getTotal().setIndexing(false);
            indexingResponse.setResult(true);
            isIndexing = false;
            siteEntitySet.clear();
            return indexingResponse;
        }
        indexingResponse.setResult(false);
        indexingResponse.setError("Индексация не запущена");
        return indexingResponse;
    }

    private void clearAllTables(){
        indexJpaRepository.deleteAllInBatch();
        lemmaJpaRepository.deleteAllInBatch();
        pageJpaRepository.deleteAllInBatch();
        siteJpaRepository.deleteAllInBatch();
    }

    public boolean isIndexing() {
        return isIndexing;
    }
}
