package base.api.shared.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Lenient converter so legacy DB values (e.g. {@code open}) do not crash reads.
 */
@Converter(autoApply = false)
public class ShiftStatusConverter implements AttributeConverter<ShiftStatus, String> {

    @Override
    public String convertToDatabaseColumn(ShiftStatus attribute) {
        return attribute == null ? ShiftStatus.DRAFT.name() : attribute.name();
    }

    @Override
    public ShiftStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return ShiftStatus.DRAFT;
        }
        String normalized = dbData.trim().toUpperCase();
        return switch (normalized) {
            case "OPEN", "OPENED", "ACTIVE" -> ShiftStatus.PUBLISHED;
            case "CLOSED", "CLOSE", "COMPLETED", "DONE" -> ShiftStatus.CANCELLED;
            case "PENDING" -> ShiftStatus.DRAFT;
            default -> {
                try {
                    yield ShiftStatus.valueOf(normalized);
                } catch (IllegalArgumentException ex) {
                    yield ShiftStatus.DRAFT;
                }
            }
        };
    }
}
