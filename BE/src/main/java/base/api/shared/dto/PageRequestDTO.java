package base.api.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tham số phân trang dùng chung. page bắt đầu từ 1.")
public class PageRequestDTO {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    @Schema(description = "Số trang (bắt đầu từ 1)", example = "1", defaultValue = "1")
    private int page = 1;  // 1-based
    @Schema(description = "Số bản ghi mỗi trang", example = "20", defaultValue = "20")
    private int size = DEFAULT_PAGE_SIZE;

    @Schema(description = "Free-text search. Blank values are ignored.", example = "milk")
    private String search;

    @Schema(description = "Field used for sorting. Each endpoint exposes its own allowlist.", example = "createdAt")
    private String sortBy;

    @Schema(description = "Sort direction: asc or desc", example = "desc", defaultValue = "desc")
    private String sortDir = "desc";

    public Pageable toPageable() {
        int zeroBasedPage = Math.max(0, page - 1);
        return PageRequest.of(zeroBasedPage, normalizedSize());
    }

    public Pageable toPageable(
            String defaultSort,
            Sort.Direction defaultDirection,
            Set<String> allowedSortFields
    ) {
        String requestedSort = normalizeNullable(sortBy);
        String safeSort = requestedSort != null && allowedSortFields.contains(requestedSort)
                ? requestedSort
                : defaultSort;
        Sort.Direction safeDirection = parseDirection(sortDir, defaultDirection);
        return PageRequest.of(
                Math.max(0, page - 1),
                normalizedSize(),
                Sort.by(safeDirection, safeSort)
        );
    }

    public String normalizedSearch() {
        return normalizeNullable(search);
    }

    private int normalizedSize() {
        return Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    private Sort.Direction parseDirection(String value, Sort.Direction fallback) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return fallback;
        }
        return "asc".equals(normalized.toLowerCase(Locale.ROOT))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
