package searchengine.services;

import org.jsoup.Jsoup;
import searchengine.dto.statistics.FoundSearchableText;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.PageRepository;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnippetFactory implements Runnable {
    private PageRepository pageRepository;
    private IndexRepository indexRepository;
    private LemmaFinderService lemmaFinderService;
    private List<LemmaEntity> searchLemmasList;
    private String searchableText;
    private String path;
    private SiteEntity siteEntity;
    private SearchResponse searchResponse;
    private Set<String> snippetSet = new HashSet<>();

    public SnippetFactory(String path, List<LemmaEntity> searchLemmasList,
                          SiteEntity siteEntity, String searchableText, SearchResponse searchResponse,
                          PageRepository pageRepository, IndexRepository indexRepository,
                          LemmaFinderService lemmaFinderService) {
        this.searchLemmasList = searchLemmasList;
        this.searchableText = searchableText;
        this.path = path;
        this.siteEntity = siteEntity;
        this.searchResponse = searchResponse;
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        this.lemmaFinderService = lemmaFinderService;
    }

    @Override
    public void run() {
        String content = pageRepository.findContentByPathAndSiteId(path, siteEntity.getId());
        String title = Jsoup.parse(content).title();
        content = Jsoup.parse(content).text();
        //Если поиск по полный фразе дал результат, сохраняем результат, переходим к следующей странице
        String snippet = searchByTheSearchableText(content);
        if (!(snippet.equals(""))) {
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
            int rank = indexRepository.findRankByLemmaIdAndPathAndSiteId(lemmaEntity.getLemma(), path, siteEntity.getId());
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
        String[] arrayWords = content.split("[\\s-)(]+");
        Set<String> arrayWordsSet = new HashSet<>(Arrays.asList(arrayWords));
        String snippet = "";
        for (String word : arrayWordsSet) {
            if (word.length() < 3) continue;
            //получаем лемму слова
            wordLemmaMap = lemmaFinderService.collectLemmas(word);
            if (wordLemmaMap.isEmpty()) continue;
            for (String lemma : wordLemmaMap.keySet()) {
                //если лемма слова равна искомой лемме находим ее в контенте и все найденные результаты
                //сохраняем в snippet, разделяя многоточием
                if (lemma.equals(lemmaEntity.getLemma())) {
                    snippet = snippet + createSnippetByLemma(word, content);
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
            snippet = createSnippetBySearchableText(content);
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
        Pattern pattern = Pattern.compile("\\b.{0,30}" + word + ".{0,80}\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String text = "..." + content.substring(start, end) + "... ";
            if (snippet.contains(text)) continue;
            snippetSet.add(text);
            text = Pattern.compile(word, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).
                    matcher(text).replaceAll("<b>" + word + "</b>");
            snippet = snippet + text;

        }
        return snippet;
    }

    private String createSnippetBySearchableText(String content) {
        String snippet = "";
        //находим искомый текст с частью текста слева и справа, согласно шаблону и сохраняем в snippet
        Pattern pattern = Pattern.compile("\\b.{0,40}\\b" + searchableText + "\\b.{0,100}\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String text = "..." + content.substring(start, end) + "... ";
            if (snippetSet.contains(text)) continue;
            snippetSet.add(text);
            text = Pattern.compile(searchableText, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).
                    matcher(text).replaceAll("<b>" + searchableText + "</b>");
            snippet = snippet + text;
        }
        return snippet;
    }
}