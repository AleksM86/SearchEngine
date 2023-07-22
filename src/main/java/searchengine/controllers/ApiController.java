package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;
import searchengine.config.Site;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.interfases.services.IndexPageService;
import searchengine.interfases.services.IndexingService;
import searchengine.interfases.services.SearchService;
import searchengine.interfases.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;
    private final IndexPageService indexPageService;


    public ApiController(StatisticsService statisticsService, IndexingService indexingService, SearchService searchService,
                         IndexPageService indexPageService) {

        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
        this.indexPageService = indexPageService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }


    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(Site site) {
        return ResponseEntity.ok(indexPageService.indexPage(site));
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam(name = "query") String query,
                                                 @Nullable @RequestParam(name = "site") String urlSite) {
        return ResponseEntity.ok(searchService.search(query, urlSite));
    }
}
