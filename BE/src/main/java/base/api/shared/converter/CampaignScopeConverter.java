package base.api.shared.converter;

import base.api.shared.enums.CampaignScope;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CampaignScopeConverter implements AttributeConverter<CampaignScope, String> {

    @Override
    public String convertToDatabaseColumn(CampaignScope attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CampaignScope convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return CampaignScope.valueOf(dbData.trim().replace(" ", "_").toUpperCase());
    }
}
