package base.api.shared.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserGenderConverterTest {

    private final UserGenderConverter converter = new UserGenderConverter();

    @Test
    void readsLegacyMixedCaseDatabaseValues() {
        assertEquals(UserGender.MALE, converter.convertToEntityAttribute("Male"));
        assertEquals(UserGender.FEMALE, converter.convertToEntityAttribute("female"));
        assertEquals(UserGender.OTHER, converter.convertToEntityAttribute(" OTHER "));
    }

    @Test
    void preservesCanonicalDatabaseFormatAndNulls() {
        assertEquals("MALE", converter.convertToDatabaseColumn(UserGender.MALE));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute("  "));
    }
}
