package base.api.feature.system.dto.response;

import java.util.List;

public record MembershipTierResponse(
        Long id,
        String code,
        String name,
        Long minPoints,
        Long maxPoints,
        Double pointMultiplier,
        List<String> benefits,
        Integer sortOrder,
        Boolean active
) {
}
