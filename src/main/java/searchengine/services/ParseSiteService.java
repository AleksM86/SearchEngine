package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.SiteStatus;
import searchengine.model.*;
import searchengine.springDataRepositorys.IndexJpaRepository;
import searchengine.springDataRepositorys.LemmaJpaRepository;
import searchengine.springDataRepositorys.PageJpaRepository;
import searchengine.springDataRepositorys.SiteJpaRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

@Service
public class ParseSiteService extends RecursiveAction {
    private static SiteJpaRepository siteJpaRepository;
    private static PageJpaRepository pageJpaRepository;
    private static LemmaJpaRepository lemmaJpaRepository;
    private static IndexJpaRepository indexJpaRepository;
    private static LemmaFinderService lemmaFinderService;
    private String url;
    private SiteEntity siteEntity;
    private ForkJoinPool fjp;
    private static volatile Boolean isStop;

    @Autowired
    public ParseSiteService(SiteJpaRepository siteJpaRepository, PageJpaRepository pageJpaRepository,
                            LemmaJpaRepository lemmaJpaRepository, IndexJpaRepository indexJpaRepository,
                            LemmaFinderService lemmaFinderService) {
        ParseSiteService.siteJpaRepository = siteJpaRepository;
        ParseSiteService.pageJpaRepository = pageJpaRepository;
        ParseSiteService.lemmaJpaRepository = lemmaJpaRepository;
        ParseSiteService.indexJpaRepository = indexJpaRepository;
        ParseSiteService.lemmaFinderService = lemmaFinderService;
    }

    public ParseSiteService(SiteEntity siteEntity) {
        this.siteEntity = siteEntity;
    }

    public ParseSiteService(String nextUrl, SiteEntity siteEntity, ForkJoinPool fjp) {
        this.url = nextUrl.replaceFirst("www\\.", "");
        this.siteEntity = siteEntity;
        this.fjp = fjp;
    }

    @Override
    protected void compute() {
        //Проверяем разрешена ли индексация, и удволетворяет ли ссылка нашим требованиям
        if (isStop) return;
        if (!url.contains(siteEntity.getUrl()) || (url.contains("#") || (url.contains("&") || (url.contains("?"))))) {
            chekParsingFinish();
            return;
        }
        //Убираем из ссылки название сайта, убираем :443 если есть для исключения одиникого контента в таблице
        String path = url.equals(siteEntity.getUrl()) ? "" : url.split(siteEntity.getUrl())[1];
        path = path.replaceAll(":443", "");
        sleeping(150);
        Document document = null;
        PageEntity pageEntity = null;
        try {
            Connection.Response response = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows; U; WindowsNT/5.1;" +
                    "en-US; rvl.8.1.6) Gecko/20070725 FireFox/2.0.0.6)").referrer("http:/www.google.com").execute();
            document = response.parse();
            pageEntity = new PageEntity(siteEntity.getId(), path, response.statusCode(), document.html());
        } catch (UnsupportedMimeTypeException e) {
        } catch (IOException e) {
            errorParsing();
        }
        //Проверяем стоит ли дальше продолжать метод для добавления данных в таблицы
        if (isCheckForExitFromMethod(pageEntity, path, document)) {
            return;
        }
        if (!addToPageTable(pageEntity)) {
            chekParsingFinish();
            return;
        }
        createParseSiteAndFork(document);
        chekParsingFinish();
    }

    // Проверка наличия страницы в таблицы и при её отстусвии добавление её в таблицу
    protected boolean addToPageTable(PageEntity pageEntity) {
        //Проверка наличия страницы и добавление данных в таблицы синхронизировано по сайту, к которой принадлежит страница
        synchronized (siteEntity) {
            siteEntity.setSiteStatus(SiteStatus.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteJpaRepository.save(siteEntity);
            if (!(pageEntity.getCode() == 200)) {
                return false;
            }
            int pageCount = pageJpaRepository.findCountByPathAndSiteId(pageEntity.getPath(), pageEntity.getSiteId());
            if (pageCount > 0) {
                return false;
            }
            pageJpaRepository.save(pageEntity);
            addToLemmaTable(pageEntity);
            return true;
        }
    }

    //Добавление лемм со страницы в таблицу, метод выполняется в одном потоке
    private void addToLemmaTable(PageEntity pageEntity) {
        Map<String, Integer> lemmas = lemmaFinderService.collectLemmas(pageEntity.getContent());
        List<LemmaEntity> lemmaEntityListForSave = new ArrayList<>();
        List<LemmaEntity> lemmaEntityListForUpdate = new ArrayList<>();
        for (String lemma : lemmas.keySet()) {
            List<LemmaEntity> lemmaEntityList = lemmaJpaRepository.findByLemmaAndSiteId(lemma, pageEntity.getSiteId());
            if (lemmaEntityList.isEmpty()) {
                LemmaEntity lemmaEntity = new LemmaEntity();
                lemmaEntity.setLemma(lemma);
                lemmaEntity.setSiteId(pageEntity.getSiteId());
                lemmaEntity.setFrequency(1);
                lemmaEntityListForSave.add(lemmaEntity);
            }
            if (!lemmaEntityList.isEmpty()) {
                LemmaEntity lemmaEntity = lemmaEntityList.get(0);
                lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
                lemmaEntityListForUpdate.add(lemmaEntity);
            }
        }
        if (!lemmaEntityListForSave.isEmpty()) {
            lemmaJpaRepository.saveAll(lemmaEntityListForSave);
        }
        if (!lemmaEntityListForUpdate.isEmpty()) {
            lemmaJpaRepository.saveAll(lemmaEntityListForUpdate);
        }
        lemmaJpaRepository.flush();
        addToIndex(lemmas, pageEntity);
    }

    // Добавление индексов в таблицу Index
    private void addToIndex(Map<String, Integer> lemmas, PageEntity pageEntity) {
        Set<String> lemmaSet = lemmas.keySet();
        List<IndexEntity> indexEntityListForSave = new ArrayList<>();
        for (String lemma : lemmaSet) {
            int rank = lemmas.get(lemma);
            int idLemma = lemmaJpaRepository.findByLemmaAndSiteId(lemma, pageEntity.getSiteId()).get(0).getId();
            IndexEntity indexEntity = new IndexEntity();
            indexEntity.setLemmaId(idLemma);
            indexEntity.setPageId(pageEntity.getId());
            indexEntity.setRank(rank);
            indexEntityListForSave.add(indexEntity);
        }
        if (!indexEntityListForSave.isEmpty()) {
            indexJpaRepository.saveAll(indexEntityListForSave);
        }
    }

    static void isStopParse() {
        isStop = true;
    }

    static void isStartParse() {
        isStop = false;
    }

    private void chekParsingFinish() {
        if (fjp.getQueuedTaskCount() == 0) {
            siteEntity.setSiteStatus(SiteStatus.INDEXED);
            siteJpaRepository.save(siteEntity);
        }
    }

    private void errorParsing() {
        siteEntity.setLastError("Страница сайта была недоступна");
        siteJpaRepository.save(siteEntity);
    }

    private void sleeping(int milliSecond) {
        try {
            Thread.sleep(milliSecond);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createParseSiteAndFork(Document document) {
        for (Element element : document.select("a")) {
            String urlNext = element.absUrl("href");
            new ParseSiteService(urlNext, siteEntity, fjp).fork();
        }
    }

    private boolean isCheckForExitFromMethod(PageEntity pageEntity, String path, Document document){
        if (pageEntity == null) {
            chekParsingFinish();
            return true;
        }
        if (path.equals("")) {
            createParseSiteAndFork(document);
            chekParsingFinish();
            return true;
        }
        return false;
    }
}
