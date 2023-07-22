package searchengine.services;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.FoundSearchableText;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.springDataRepositorys.IndexJpaRepository;
import searchengine.springDataRepositorys.LemmaJpaRepository;
import searchengine.springDataRepositorys.PageJpaRepository;
import searchengine.springDataRepositorys.SiteJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SnippetFactoryService implements Runnable {
    private static SiteJpaRepository siteJpaRepository;
    private static PageJpaRepository pageJpaRepository;
    private static LemmaJpaRepository lemmaJpaRepository;
    private static IndexJpaRepository indexJpaRepository;
    private static LemmaFinderService lemmaFinderService;
    private List<LemmaEntity> searchLemmasList;
    private String searchableText;
    private String path;
    private SiteEntity siteEntity;
    private SearchResponse searchResponse;


    @Autowired
    public SnippetFactoryService(SiteJpaRepository siteJpaRepository, PageJpaRepository pageJpaRepository,
                                 LemmaJpaRepository lemmaJpaRepository, IndexJpaRepository indexJpaRepository,
                                 LemmaFinderService lemmaFinderService) {
        SnippetFactoryService.siteJpaRepository = siteJpaRepository;
        SnippetFactoryService.pageJpaRepository = pageJpaRepository;
        SnippetFactoryService.lemmaJpaRepository = lemmaJpaRepository;
        SnippetFactoryService.indexJpaRepository = indexJpaRepository;
        SnippetFactoryService.lemmaFinderService = lemmaFinderService;
    }

    public SnippetFactoryService(String path, List<LemmaEntity> searchLemmasList,
                                 SiteEntity siteEntity, String searchableText, SearchResponse searchResponse) {
        this.searchLemmasList = searchLemmasList;
        this.searchableText = searchableText;
        this.path = path;
        this.siteEntity = siteEntity;
        this.searchResponse = searchResponse;
    }

    @Override
    public void run() {
        String content = pageJpaRepository.findContentByPathAndSiteId(path, siteEntity.getId());
        String title = Jsoup.parse(content).title();
        content = Jsoup.parse(content).text();
        //Если поиск по полный фразе дал результат, сохраняем результат, переходим к следующей странице
        String snippet = searchByTheSearchableText(content);
        if (!(snippet == "")) {
            snippet = cutSnippet(snippet);
            FoundSearchableText foundSearchableText = createFoundSearchableText(siteEntity.getUrl(), siteEntity.getName(),
                    path, title, snippet, (1000 * snippet.length()));
            searchResponse.getData().add(foundSearchableText);
            return;
        }
        //Если поиск по искомому тексту не дал результата ищем по леммам и сохраняем найденные результаты
        snippet = "";
        double relevanceAbsolute = 0;
        for (LemmaEntity lemmaEntity : searchLemmasList) {
            int rank = indexJpaRepository.findContentByLemmaIdAndPageId(lemmaEntity.getLemma(), path, siteEntity.getId());
            relevanceAbsolute = relevanceAbsolute + rank;
            snippet = snippet + searchSnippet(content, lemmaEntity);
            snippet = cutSnippet(snippet);
        }
        FoundSearchableText foundSearchableText = createFoundSearchableText(siteEntity.getUrl(),
                siteEntity.getName(), path, title, snippet, relevanceAbsolute);
        searchResponse.getData().add(foundSearchableText);
    }

    private String searchSnippet(String content, LemmaEntity lemmaEntity) {
        Map<String, Integer> wordLemmaMap;
        String[] arrayWords = content.split("\\s");
        String snippet = "";
        for (String word : arrayWords) {
            //В слове отсавляем только буквы
            word = word.replaceAll("[^[А-Яа-я]]", "");
            //получаем лемму слова
            wordLemmaMap = lemmaFinderService.collectLemmas(word);
            if (wordLemmaMap.isEmpty()) continue;
            for (String lemma : wordLemmaMap.keySet()) {
                //если лемма слова равна искомой лемме находим ее в контенте, и все найденные результаты
                //сохраняем в snippet, разделяя многоточием
                if (lemma.equals(lemmaEntity.getLemma())) {
                    snippet = createSnippetByLemma(word, content);
                }
            }
        }
        return snippet;
    }

    private String searchByTheSearchableText(String content) {
        String snippet = "";
        //Проверяем содержит ли страница искомый текст
        if (Pattern.compile("\\b" + searchableText + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).
                matcher(content).find()) {
            snippet = createSnippetBySearchableText(searchableText, content);
        }
        return snippet;
    }

    private FoundSearchableText createFoundSearchableText(String site, String siteName, String uri,
                                                          String title, String snippet, double relevance) {
        FoundSearchableText foundSearchableText = new FoundSearchableText();
        foundSearchableText.setSite(site);
        foundSearchableText.setSiteName(siteName);
        foundSearchableText.setUri(uri);
        foundSearchableText.setTitle(title);
        foundSearchableText.setSnippet(snippet);
        foundSearchableText.setRelevance(relevance);
        return foundSearchableText;
    }

    private String cutSnippet(String snippet) {
        //Если snippet длинее более 300 символов, обрезаем его до 300 символов
        if (snippet.length() > 300) {
            String deleteSnippet = snippet.substring(299);
            snippet = snippet.replaceAll(Pattern.quote(deleteSnippet), "") + "...";
            return snippet;
        }
        return snippet;
    }

    private String createSnippetByLemma(String word, String content) {
        String snippet = "";
        Pattern pattern = Pattern.compile("\\b.{0,15}\\b" + word + "\\b.{0,30}\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String text = "..." + content.substring(start, end) + "... ";
            text = text.replaceAll(word, "<b>" + word + "</b>");
            if (snippet.contains(text)) continue;
            snippet = snippet + text;
        }
        return snippet;
    }

    private String createSnippetBySearchableText(String searchableText, String content) {
        String snippet = "";
        Pattern pattern1 = Pattern.compile(searchableText, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher1 = pattern1.matcher(content);
        String searchableFromContent = "";
        while (matcher1.find()) {
            //определяем нахождение искомого текста в тексте страницы
            searchableFromContent = content.substring(matcher1.start(), matcher1.end());
            //находим искомый текст с частью текста слева и справа, согласно шаблону и сохраняем в snippet
            Pattern pattern2 = Pattern.compile("\\b.{0,15}\\b" + searchableFromContent + "\\b.{0,30}\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher2 = pattern2.matcher(content);
            while (matcher2.find()) {
                int start = matcher2.start();
                int end = matcher2.end();
                String text = "..." + content.substring(start, end) + "... ";
                text = Pattern.compile(searchableText, Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).
                        matcher(text).replaceAll("<b>" + searchableFromContent + "</b>");
                if (snippet.contains(text)) continue;
                snippet = snippet + text;
            }
        }
        return snippet;
    }
}
