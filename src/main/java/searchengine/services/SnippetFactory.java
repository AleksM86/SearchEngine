package searchengine.services;

import org.jsoup.Jsoup;
import searchengine.dto.statistics.FoundSearchableText;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.PageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private String title;
    private String content;
    private List<String> snippetList = new ArrayList<>();

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
        content = pageRepository.findContentByPathAndSiteId(path, siteEntity.getId());
        title = Jsoup.parse(content).title();
        content = Jsoup.parse(content).text();
        //Поиск по полной фразе
        searchByTheSearchableText();
        //Если поиск дал результаты пеерходим к следующей страницы
        if(!snippetList.isEmpty()) return;
        //Вычисление абсолютной реелвантности страницы
        double relevanceAbsolute = 0;
        for (LemmaEntity lemmaEntity : searchLemmasList) {
            int rank = indexRepository.findContentByLemmaIdAndPageId(lemmaEntity.getLemma(), path, siteEntity.getId());
            relevanceAbsolute = relevanceAbsolute + rank;
        }
        //Поиск по леммам
        for (LemmaEntity lemmaEntity : searchLemmasList) {
            searchSnippet(lemmaEntity, relevanceAbsolute);
        }
    }

    private void searchSnippet(LemmaEntity lemmaEntity, double relevanceAbsolute) {
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
                    createSnippetByLemma(word, relevanceAbsolute);
                }
            }
        }
    }

    private void searchByTheSearchableText() {
        //Проверяем содержит ли страница искомый текст
        if (Pattern.compile("\\b" + searchableText + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).
                matcher(content).find()) {
            createSnippetBySearchableText();
        }
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

    private void createSnippetByLemma(String word, double relevanceAbsolute) {
        Pattern pattern = Pattern.compile("\\b.{0,60}\\b" + word + "\\b.{0,100}\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String text = "..." + content.substring(start, end) + "... ";
            text = Pattern.compile(word, Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).
                    matcher(text).replaceAll("<b>" + word + "</b>");
            if (snippetList.contains(text)) continue;
            FoundSearchableText foundSearchableText = createFoundSearchableText(siteEntity.getUrl(), siteEntity.getName(),
                    path, title, text, relevanceAbsolute);
            searchResponse.getData().add(foundSearchableText);
            snippetList.add(text);
        }
    }

    private void createSnippetBySearchableText() {
        Pattern pattern1 = Pattern.compile(searchableText, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher1 = pattern1.matcher(content);
        String searchableFromContent = "";
        while (matcher1.find()) {
            //определяем нахождение искомого текста в тексте страницы
            searchableFromContent = content.substring(matcher1.start(), matcher1.end());
            //находим искомый текст с частью текста слева и справа, согласно шаблону и сохраняем в snippet
            Pattern pattern2 = Pattern.compile("\\b.{0,60}\\b" + searchableFromContent + "\\b.{0,100}\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher2 = pattern2.matcher(content);
            while (matcher2.find()) {
                int start = matcher2.start();
                int end = matcher2.end();
                String text = "..." + content.substring(start, end) + "... ";
                text = Pattern.compile(searchableText, Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).
                        matcher(text).replaceAll("<b>" + searchableFromContent + "</b>");
                if (snippetList.contains(text)) continue;
                FoundSearchableText foundSearchableText = createFoundSearchableText(siteEntity.getUrl(), siteEntity.getName(),
                        path, title, text, 1000);
                searchResponse.getData().add(foundSearchableText);
                snippetList.add(text);
            }
        }
    }
}
