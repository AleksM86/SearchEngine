package searchengine.dto.statistics;

import lombok.Data;

@Data
public class IndexingResponse {
    private Boolean result;
    private String error = "";
}
