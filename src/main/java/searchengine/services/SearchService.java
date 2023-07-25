package searchengine.services;

import searchengine.dto.statistics.SearchResponse;

public interface SearchService {
    SearchResponse search(String searchableText, String urlSite);
}
