package base.api.shared.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductScope {
    GLOBAL("GLOBAL"),
    BRANCH("BRANCH");

    private final String value;

    public static ProductScope fromValue(String value) {
        if (value == null || value.isBlank()) {
            return GLOBAL;
        }
        return "BRANCH".equalsIgnoreCase(value.trim()) ? BRANCH : GLOBAL;
    }
}
