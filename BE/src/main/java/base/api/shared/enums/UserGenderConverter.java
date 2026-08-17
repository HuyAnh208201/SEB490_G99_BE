package base.api.shared.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter
public class UserGenderConverter implements AttributeConverter<UserGender, String> {

    @Override
    public String convertToDatabaseColumn(UserGender attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public UserGender convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return UserGender.valueOf(dbData.trim().toUpperCase(Locale.ROOT));
    }
}
