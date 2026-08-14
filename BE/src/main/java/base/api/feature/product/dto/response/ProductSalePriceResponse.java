package base.api.feature.product.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProductSalePriceResponse {
    private Long id;
    private Integer productId;
    private BigDecimal price;
    private LocalDate effectiveDate;
    private Long createdBy;
    private LocalDateTime createdAt;
}
