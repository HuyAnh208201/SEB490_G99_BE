package base.api.feature.voucher.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Issues codes: one for a named customer, or a batch of shared codes. */
@Getter
@Setter
public class IssueVoucherRequest {

    @NotNull(message = "Discount type is required.")
    private Long voucherCatalogId;

    /**
     * users.id of the customer the code is reserved for. Empty means a shared code that
     * anyone may apply.
     */
    private Long customerId;

    /** Code prefix, to keep codes readable at the counter; empty uses the default. */
    @Size(max = 16, message = "Code prefix must not exceed 16 characters.")
    private String codePrefix;

    /** How many codes to generate. A code reserved for a customer is always exactly one. */
    @Min(value = 1, message = "Quantity must be at least 1.")
    @Max(value = 200, message = "Cannot issue more than 200 codes at once.")
    private Integer quantity;

    @NotNull(message = "Expiry date is required.")
    private LocalDateTime expiresAt;
}
