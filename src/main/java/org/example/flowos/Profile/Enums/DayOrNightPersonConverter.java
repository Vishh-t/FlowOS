package org.example.flowos.Profile.Enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DayOrNightPersonConverter implements AttributeConverter<DayOrNightPersonEnum, String>
{
    @Override
    public String convertToDatabaseColumn(DayOrNightPersonEnum attribute)
    {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public DayOrNightPersonEnum convertToEntityAttribute(String dbData)
    {
        return dbData == null ? null : DayOrNightPersonEnum.fromCode(dbData);
    }
}
