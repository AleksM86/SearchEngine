package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.FoundSearchableText;
import searchengine.dto.statistics.SearchResponse;
import searchengine.dto.statistics.SiteStatus;
import searchengine.interfases.services.SearchService;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.springDataRepositorys.IndexJpaRepository;
import searchengine.springDataRepositorys.LemmaJpaRepository;
import searchengine.springDataRepositorys.PageJpaRepository;
import searchengine.springDataRepositorys.SiteJpaRepository;
import java.util.*;
import java.util.concurrent.*;

@Service
public class SearchServiceImpl implements SearchService {
    @Autowired
    private SiteJpaRepository siteJpaRepository;
    @Autowired
    private PageJpaRepository pageJpaRepository;
    @Autowired
    private LemmaJpaRepository lemmaJpaRepository;
    @Autowired
    private IndexJpaRepository indexJpaRepository;
    @Autowired
    private LemmaFinderService lemmaFinderService;
    private Map<String, Integer> searchLemmas = new HashMap<>();
    private SearchResponse searchResponse;
    private String searchableText;

    @Override
    public SearchResponse search(String searchableText, String urlSite) {
        this.searchableText = searchableText.strip();
        searchResponse = new SearchResponse();
        if (isSearchableTextEmpty(searchableText)) {
            return searchResponse;
        }
        searchLemmas = lemmaFinderService.collectLemmas(searchableText);
        List<SiteEntity> siteEntityList = null;
        //Если сайт не указан, индексация происходит по всем индексированным сайтам
        if (urlSite == null) {
            siteEntityList = siteJpaRepository.findAll();
            //Проверяем проиндексированны ли сайты
            if (!isAllSiteIndexed(siteEntityList)) {
                return searchResponse;
            }
        }
        //Если сайт указан в запросе, то поиск только по нему
        if (!(urlSite == null)) {
            siteEntityList = siteJpaRepository.findByUrl(urlSite);
            //Проверяем проиндексирован ли сайт
            if (!isOneSiteIndexed(siteEntityList)) {
                return searchResponse;
            }
        }
        //Запускаем поиск по сфомированному списку сайтов
        searchInSiteEntityList(siteEntityList);
        calculatingRelativeRelevance(searchResponse.getData());
        sortData(searchResponse.getData());
        return searchResponse;
    }

    private void searchInSiteEntityList(List<SiteEntity> siteEntityList) {
        //Поиск по каждомку сайту по очереди
        for (SiteEntity siteEntity : siteEntityList) {
            int siteId = siteEntity.getId();
            //Формируем отсортированный список сущностей Лемм из искомых слов
            List<LemmaEntity> searchLemmasList = createSortedSearchLemmasList(siteEntity);
            if (searchLemmasList.isEmpty()) {
                continue;
            }
            //Получаем список страниц у самой редкой леммы
            List<String> pathList = indexJpaRepository.findPathsByLemmaId(searchLemmasList.get(0).getId());
//            //Получаем отсортированный список страниц, где всчтречаются только все леммы на каждой странице
            List<String> sortedPathList = createSortedPathList(pathList, searchLemmasList, siteId);
            if (sortedPathList.isEmpty()) {
                continue;
            }
            //Создаем объекты результата поиска
            createFoundTextList(sortedPathList, searchLemmasList, siteId);
        }
        searchResponse.setCount(searchResponse.getData().size());
        if (searchResponse.getData().isEmpty()) {
            searchResponse.setResult(false);
            searchResponse.setError("Поиск не дал результатов");
        }
    }

    private List createSortedSearchLemmasList(SiteEntity siteEntity) {
        //В список добавляются сущности лемм которые встречаются на сайте и у которых
        //коэффициент встречаемости ниже указанного
        int countPages = pageJpaRepository.findCountBySiteId(siteEntity.getId());
        ArrayList<LemmaEntity> lemmaEntityList = new ArrayList<>();
        for (String searchLemma : searchLemmas.keySet()) {
            LemmaEntity lemmaEntity = null;
            try {
                lemmaEntity = lemmaJpaRepository.findByLemmaAndSiteId(searchLemma, siteEntity.getId()).get(0);
            } catch (IndexOutOfBoundsException e) {
                continue;
            }
            int countSearchLemmas = indexJpaRepository.findCountIndexByLemmaId(lemmaEntity.getId());
            if (countSearchLemmas == 0 || ((double) countSearchLemmas / (double) countPages > 0.7)) {
                continue;
            }
            lemmaEntityList.add(lemmaEntity);
        }
        sortedList(lemmaEntityList);
        return lemmaEntityList;
    }

    private List sortedList(ArrayList<LemmaEntity> list) {
        list.sort((o1, o2)
                -> Integer.valueOf(o1.getFrequency()).compareTo(
                o2.getFrequency()));
        return list;
    }

    private List createSortedPathList(List<String> pathList, List<LemmaEntity> searchLemmasList, int siteId) {
        List<String> sortedPathList = new ArrayList<>(pathList);
        for (String path : pathList) {
            int pageId = pageJpaRepository.findPageIdCountByPathAndSiteId(path, siteId);
            for (LemmaEntity lemmaEntity : searchLemmasList) {
                int countLemmaInPage = indexJpaRepository.findCountIndexByLemmaIdAndPageId(lemmaEntity.getId(), pageId);
                if (countLemmaInPage == 0) {
                    sortedPathList.remove(path);
                    continue;
                }
            }
        }
        return sortedPathList;
    }

    private void createFoundTextList(List<String> sortedPathList, List<LemmaEntity> searchLemmasList, int siteId) {
        SiteEntity siteEntity = siteJpaRepository.getReferenceById(siteId);
        ExecutorService executorService = Executors.newWorkStealingPool();
        // SnippetFactoryService.createLemmaFinder();
        //Обект с результатами поиска создается в классе SnippetFactoryService
        for (String path : sortedPathList) {
            SnippetFactoryService task = new SnippetFactoryService(path, searchLemmasList, siteEntity, searchableText, searchResponse);
            executorService.execute(task);
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(60, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sortData(List<FoundSearchableText> data) {
        data.sort((o1, o2)
                -> Double.valueOf(o1.getRelevance()).compareTo(
                o2.getRelevance()));
        Collections.reverse(data);
    }

    private void calculatingRelativeRelevance(List<FoundSearchableText> data) {
        double maxRelevance = 0;
        for (FoundSearchableText foundSearchableText : data) {
            if (foundSearchableText.getRelevance() > maxRelevance) maxRelevance = foundSearchableText.getRelevance();
        }
        for (FoundSearchableText foundSearchableText : data) {
            foundSearchableText.setRelevance(foundSearchableText.getRelevance() / maxRelevance);
        }
    }

    private boolean isSearchableTextEmpty(String searchableText) {
        if (searchableText.isEmpty()) {
            searchResponse.setResult(false);
            searchResponse.setError("Пустой поисковый запрос");
            return true;
        }
        return false;
    }

    private boolean isAllSiteIndexed(List<SiteEntity> siteEntityList) {
        //Проверяем есть ли сайты в таблице
        if (siteEntityList.isEmpty()) {
            searchResponse.setResult(false);
            searchResponse.setError("Сайты не проиндексированы");
            return false;
        }
        //Проверяем у всех ли сайтов статус INDEXED
        for (SiteEntity siteEntity : siteEntityList) {
            if (!siteEntity.getSiteStatus().equals(SiteStatus.INDEXED)) {
                searchResponse.setResult(false);
                searchResponse.setError("Сайты не проиндексированы");
                return false;
            }
        }
        return true;
    }

    private boolean isOneSiteIndexed(List<SiteEntity> siteEntityList) {
        //Проверяем есть ли сайт в таблице
        if (siteEntityList.isEmpty()) {
            searchResponse.setResult(false);
            searchResponse.setError("Сайт не проиндексирован");
            return false;
        }
        //Проверяем имеет ли сайт статус INDEXED
        for (SiteEntity siteEntity : siteEntityList) {
            if (!siteEntity.getSiteStatus().equals(SiteStatus.INDEXED)) {
                searchResponse.setResult(false);
                searchResponse.setError("Сайт не проиндексирован");
                return false;
            }
        }
        return true;
    }
}