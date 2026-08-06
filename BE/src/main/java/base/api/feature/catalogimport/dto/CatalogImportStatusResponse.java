package base.api.feature.catalogimport.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatalogImportStatusResponse {
    private String status; // IDLE | RUNNING | COMPLETED | FAILED
    private int total;
    private int created;
    private int skipped;
    private int failed;
    private int processed;
    private String message;
    private String startedAt;
    private String finishedAt;
}
