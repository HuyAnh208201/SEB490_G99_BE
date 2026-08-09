package base.api.feature.promotion.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class UpdateCampaignRequest {

    @NotBlank(message = "Promotion name is required.")
    @Size(max = 255, message = "Promotion name must not exceed 255 characters.")
    private String name;

    @NotBlank(message = "Promotion type is required.")
    private String type;

    @NotNull(message = "Discount value is required.")
    @DecimalMin(value = "0", message = "Discount value must be greater than or equal to 0.")
    private BigDecimal discountValue;

    private JsonNode conditions;

    @NotNull(message = "Priority is required.")
    @Min(value = 0, message = "Priority must be greater than or equal to 0.")
    private Integer priority;

    @NotNull(message = "Start date is required.")
    private LocalDateTime startAt;

    @NotNull(message = "End date is required.")
    private LocalDateTime endAt;

    /** CHAIN or BRANCH — Admin/Director must keep CHAIN; Branch Manager keeps BRANCH. */
    private String scope;

    /**
     * For CHAIN: empty/null = entire chain; non-empty = specific branches.
     * Ignored for Branch Manager updates (branch mapping stays on their store).
     */
    private List<Long> branchIds;

    @AssertTrue(message = "End date must be after start date.")
    public boolean isEndAtAfterStartAt() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }
}
