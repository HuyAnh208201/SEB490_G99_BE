package base.api.feature.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateMembershipTierRequest {

    @NotBlank
    private String name;

    @NotNull
    @PositiveOrZero
    private Long minPoints;

    /** Inclusive upper bound; null = open-ended. */
    @PositiveOrZero
    private Long maxPoints;

    @NotNull
    @Positive
    private Double pointMultiplier;

    private List<String> benefits;

    @NotNull
    @PositiveOrZero
    private Integer sortOrder;

    @NotNull
    private Boolean active;
}
