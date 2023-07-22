package searchengine.interfases.services;

import searchengine.config.Site;
import searchengine.dto.statistics.IndexingResponse;

public interface IndexPageService {
    IndexingResponse indexPage(Site site);
}
