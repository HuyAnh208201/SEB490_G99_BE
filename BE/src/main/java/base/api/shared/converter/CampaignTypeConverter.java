package base.api.shared.converter;

import base.api.shared.enums.CampaignType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CampaignTypeConverter implements AttributeConverter<CampaignType, String> {

    @Override
    public String convertToDatabaseColumn(CampaignType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CampaignType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return CampaignType.valueOf(dbData.trim().replace(" ", "_").toUpperCase());
    }
}
