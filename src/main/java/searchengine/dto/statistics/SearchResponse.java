package searchengine.dto.statistics;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SearchResponse {
    private boolean result = true;
    private int count;
    private List<FoundSearchableText> data = new ArrayList<>();
    private String error = "";
}
