package base.api.shared.converter;

import base.api.shared.enums.PurchaseRequestStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PurchaseRequestStatusConverter implements AttributeConverter<PurchaseRequestStatus, String> {

    @Override
    public String convertToDatabaseColumn(PurchaseRequestStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public PurchaseRequestStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        String normalized = dbData.trim().replace(" ", "_").toUpperCase();
        if ("SUBMITTED".equals(normalized)) {
            return PurchaseRequestStatus.PENDING;
        }
        return PurchaseRequestStatus.valueOf(normalized);
    }
}
