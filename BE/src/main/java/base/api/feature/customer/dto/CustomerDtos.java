package base.api.feature.customer.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    @Data
    public static class ProfileResponse {
        private Long id;
        private String fullName;
        private String phone;
        private String email;
        private LocalDate dateOfBirth;
        private String gender;
        private Long points;
        private String tierCode;
        private String tierName;
        private Double pointMultiplier;
        private List<String> tierBenefits;
        private Long lifetimeEarnedPoints;
        private LocalDateTime memberSince;
        private String qrPayload;
    }

    @Data
    public static class UpdateProfileRequest {
        @Size(min = 2, max = 120)
        private String fullName;
        private LocalDate dateOfBirth;
        @Size(max = 32)
        private String gender;
    }

    @Data
    public static class PointHistoryItem {
        private Long id;
        private Long orderId;
        private String invoiceCode;
        private Long points;
        private Long pointsEarned;
        private BigDecimal orderTotal;
        private String type;
        private LocalDateTime createdAt;
        private String label;
    }

    @Data
    public static class PageResponse<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Data
    public static class LoyaltyConfigResponse {
        private long vndPerPoint;
        private long pointValueVnd;
    }

    @Data
    public static class TierResponse {
        private Long id;
        private String code;
        private String name;
        private Long minPoints;
        private Long maxPoints;
        private Double pointMultiplier;
        private List<String> benefits;
        private Integer sortOrder;
        private boolean current;
    }

    @Data
    public static class QrResponse {
        private String phone;
        private String payload;
    }
}
