package base.api.feature.product.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ScheduleProductSalePriceRequest {
    @NotNull(message = "Retail price is required.")
    private BigDecimal price;

    @NotNull(message = "Effective date is required.")
    private LocalDate effectiveDate;
}
