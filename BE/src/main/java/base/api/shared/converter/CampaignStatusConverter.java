package base.api.shared.converter;

import base.api.shared.enums.CampaignStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CampaignStatusConverter implements AttributeConverter<CampaignStatus, String> {

    @Override
    public String convertToDatabaseColumn(CampaignStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CampaignStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        String normalized = dbData.trim().replace(" ", "_").toUpperCase();
        if ("DEACTIVATE".equals(normalized) || "INACTIVE".equals(normalized)) {
            return CampaignStatus.DEACTIVATED;
        }
        return CampaignStatus.valueOf(normalized);
    }
}
