package base.api.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tham số phân trang dùng chung. page bắt đầu từ 1.")
public class PageRequestDTO {
    @Schema(description = "Số trang (bắt đầu từ 1)", example = "1", defaultValue = "1")
    private int page = 1;  // 1-based
    @Schema(description = "Số bản ghi mỗi trang", example = "10", defaultValue = "10")
    private int size = 10;

    public Pageable toPageable() {
        int zeroBasedPage = Math.max(0, page - 1);
        return PageRequest.of(zeroBasedPage, size);
    }
}
