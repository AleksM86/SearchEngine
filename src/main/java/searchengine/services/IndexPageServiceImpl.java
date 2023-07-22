package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.SiteStatus;
import searchengine.interfases.services.IndexPageService;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.springDataRepositorys.IndexJpaRepository;
import searchengine.springDataRepositorys.LemmaJpaRepository;
import searchengine.springDataRepositorys.PageJpaRepository;
import searchengine.springDataRepositorys.SiteJpaRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class IndexPageServiceImpl implements IndexPageService {
    @Autowired
    private SiteJpaRepository siteJpaRepository;
    @Autowired
    private PageJpaRepository pageJpaRepository;
    @Autowired
    private LemmaJpaRepository lemmaJpaRepository;
    @Autowired
    private IndexJpaRepository indexJpaRepository;
    IndexingResponse indexingResponse = new IndexingResponse();
    private final SitesList sites;
    private PageEntity pageEntity;
    private SiteEntity siteEntity;
    private String path;
    private Site site;
    ParseSiteService parseSiteService;

    public IndexPageServiceImpl(SitesList sites) {
        this.sites = sites;
        for (Site site : sites.getSites()) {
            site.setUrl(site.getUrl().replaceFirst("www\\.", ""));
        }
    }

    @Override
    public IndexingResponse indexPage(Site siteParam) {
        indexingResponse.setResult(false);
        indexingResponse.setError("Данная страница сайтов находится за пределами сайтов, указанных в конфигурационном файле");
        String urlParam = siteParam.getUrl().replaceFirst("www\\.", "").strip();

        //Проверяем соответствует ли указанный адрес страницы сайтам из конфигурационного файла
        indexingResponse = chekUrlInApplication(urlParam);
        if (!indexingResponse.getResult()) return indexingResponse;

        List<PageEntity> pageEntityList = pageJpaRepository.findPageEntityListByPathIdAndSiteUrl(path, site.getUrl());

        //Проверяем есть ли страница в базе данных. Если есть удаляем ее индексы,
        //далее создаем новую сущность страницы
        if (!(pageEntityList.isEmpty())) {
            deletePageFromTables(pageEntity);
        }
        pageEntity = createPageEntity(urlParam);
        siteEntity = pageEntity.getSiteEntity();
        if (pageEntity == null) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Страница сайта была недоступна");
            return indexingResponse;
        }
        //Отправляем страницу на индексацию

        parseSiteService = new ParseSiteService(siteEntity);
        parseSiteService.addToPageTable(pageEntity);
        siteEntity.setSiteStatus(SiteStatus.INDEXED);
        siteJpaRepository.save(siteEntity);
        return indexingResponse;
    }

    private void deletePageFromTables(PageEntity pageEntity) {
        //Удаляем данные из баззы данных, связанные с этой страницей
        List<IndexEntity> indexEntityListForDelete = indexJpaRepository.findByPageEntity(pageEntity.getId());
        indexJpaRepository.deleteAll(indexEntityListForDelete);
    }

    private PageEntity createPageEntity(String urlParam) {
        List<SiteEntity> siteEntityList = siteJpaRepository.findByUrl(urlParam);
        //Если сайта в базе данных не, то создаем
        if (siteEntityList.isEmpty()) {
            siteEntity = new SiteEntity();
            siteEntity.setSiteStatus(SiteStatus.INDEXED);
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatusTime(LocalDateTime.now());
            siteJpaRepository.save(siteEntity);
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
            siteJpaRepository.save(siteEntity);
        }
        return pageEntity;
    }

    private IndexingResponse chekUrlInApplication(String urlParam) {
        for (Site site : sites.getSites()) {
            if (urlParam.contains(site.getUrl())) {
                path = urlParam.equals(site.getUrl()) ? "" : site.getUrl().split(site.getUrl())[1];
                this.site = site;
                indexingResponse.setResult(true);
                indexingResponse.setError("");
                break;
            }
        }
        return indexingResponse;
    }
}
