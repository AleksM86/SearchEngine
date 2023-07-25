package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.dto.statistics.SiteStatus;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
public class IndexingServiceImpl implements IndexingService {
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private LemmaFinderService lemmaFinderService;
    private boolean isIndexing;
    private final SitesList sites;
    private Set<SiteEntity> siteEntitySet = new HashSet<>();
    private Site site;
    private IndexingResponse indexingResponse;

    public IndexingServiceImpl(SitesList sites) {
        this.sites = sites;
        for (Site site : sites.getSites()) {
            site.setUrl(site.getUrl().replaceFirst("www\\.", "").strip());
        }
    }

    @Override
    public IndexingResponse startIndexing() {
        indexingResponse = new IndexingResponse();
        isIndexing = checkIsIndexing();
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
        ParseSite.isStartParse();
        ParseSite.createStaticRepository(siteRepository,pageRepository,lemmaRepository,indexRepository,lemmaFinderService);
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
                ParseSite parseSite = new ParseSite(site.getUrl(), siteEntity, fjp);
                fjp.execute(parseSite);
            }
            catch (Exception e) {
                siteEntity.setSiteStatus(SiteStatus.FAILED);
                siteEntity.setLastError("Сайт не доступен");
                siteRepository.save(siteEntity);
                e.printStackTrace();
                continue;
            }
            siteRepository.save(siteEntity);
        }
        if (siteEntitySet.isEmpty()) {
            StatisticsServiceImpl.getTotal().setIndexing(false);
        }
    }

    public IndexingResponse stopIndexing() {
        IndexingResponse indexingResponse = new IndexingResponse();
        if (isIndexing) {
            //Прекращаем прием задач в пул потоков
            ParseSite.isStopParse();
            for (SiteEntity siteEntity : siteEntitySet) {
                siteEntity.setSiteStatus(SiteStatus.INDEXED);
                siteEntity.setLastError("Индексация отстановленна пользователем");
                siteRepository.save(siteEntity);
            }
            StatisticsServiceImpl.getTotal().setIndexing(false);
            indexingResponse.setResult(true);
            siteEntitySet.clear();
            return indexingResponse;
        }
        indexingResponse.setResult(false);
        indexingResponse.setError("Индексация не запущена");
        return indexingResponse;
    }

    private void clearAllTables(){
        indexRepository.deleteAllInBatch();
        lemmaRepository.deleteAllInBatch();
        pageRepository.deleteAllInBatch();
        siteRepository.deleteAllInBatch();
    }
    private boolean checkIsIndexing(){
        List<SiteEntity> siteEntityList = siteRepository.findAll();
        if (siteEntityList.isEmpty()){
            return false;
        }
        for (SiteEntity siteEntity : siteEntityList){
            if (siteEntity.getSiteStatus().equals(SiteStatus.INDEXING)){
                return true;
            }
        }
        return false;
    }
    @Override
    public IndexingResponse indexPage(Site siteParam) {
        ParseSite.createStaticRepository(siteRepository,pageRepository,lemmaRepository,indexRepository,lemmaFinderService);
        indexingResponse = new IndexingResponse();
        indexingResponse.setResult(false);
        indexingResponse.setError("Данная страница сайтов находится за пределами сайтов, указанных в конфигурационном файле");
        String urlParam = siteParam.getUrl().replaceFirst("www\\.", "").strip();

        //Проверяем соответствует ли указанный адрес страницы сайтам из конфигурационного файла
        String path = chekUrlInApplication(urlParam);
        if (path.equals("")) return indexingResponse;

        List<PageEntity> pageEntityList = pageRepository.findPageEntityListByPathIdAndSiteUrl(path, site.getUrl());

        //Проверяем есть ли страница в базе данных. Если есть удаляем ее индексы,
        //далее создаем новую сущность страницы
        if (!(pageEntityList.isEmpty())) {
            deletePageFromTables(pageEntityList.get(0));
        }
        PageEntity pageEntity = createPageEntity(path, urlParam);
        SiteEntity siteEntity = pageEntity.getSiteEntity();
        if (pageEntity == null) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Страница сайта была недоступна");
            return indexingResponse;
        }
        //Отправляем страницу на индексацию

        ParseSite parseSite = new ParseSite(siteEntity);
        parseSite.addToPageTable(pageEntity);
        siteEntity.setSiteStatus(SiteStatus.INDEXED);
        siteRepository.save(siteEntity);
        return indexingResponse;
    }
    private void deletePageFromTables(PageEntity pageEntity) {
        //Удаляем данные из баззы данных, связанные с этой страницей
        List<IndexEntity> indexEntityListForDelete = indexRepository.findByPageEntity(pageEntity.getId());
        indexRepository.deleteAll(indexEntityListForDelete);
    }
    private PageEntity createPageEntity(String path, String urlParam) {
        List<SiteEntity> siteEntityList = siteRepository.findByUrl(site.getUrl());
        SiteEntity siteEntity = null;
        PageEntity pageEntity = null;
        //Если сайта в базе данных не, то создаем
        if (siteEntityList.isEmpty()) {
            System.out.println(site.getUrl());
            siteEntity = new SiteEntity();
            siteEntity.setSiteStatus(SiteStatus.INDEXED);
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }
        //Если сайт страницы есть, берем его из базы
        if (!siteEntityList.isEmpty()) {
            siteEntity = siteEntityList.get(0);
        }
        //Создаем сущность страницы
        try {
            Connection.Response response = Jsoup.connect(urlParam).userAgent("Mozilla/5.0 (Windows; U; WindowsNT/5.1;" +
                    "en-US; rvl.8.1.6) Gecko/20070725 FireFox/2.0.0.6)").referrer("http:/www.google.com").execute();
            Document document = response.parse();
            pageEntity = new PageEntity(siteEntity.getId(), path, response.statusCode(), document.html());
            pageEntity.setSiteEntity(siteEntity);
        } catch (IOException e) {
            siteEntity.setLastError("Страница сайта была недоступна");
            siteRepository.save(siteEntity);
        }
        return pageEntity;
    }
    private String chekUrlInApplication(String urlParam) {
        String path = "";
        for (Site site : sites.getSites()) {
            if (urlParam.contains(site.getUrl())) {
                path = urlParam.equals(site.getUrl()) ? "" : urlParam.split(site.getUrl())[1];
                this.site = site;
                indexingResponse.setResult(true);
                indexingResponse.setError("");
                break;
            }
        }
        return path;
    }
}
