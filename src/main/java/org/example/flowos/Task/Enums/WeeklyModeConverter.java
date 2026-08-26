package org.example.flowos.Task.Enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class WeeklyModeConverter implements AttributeConverter<WeeklyModeEnum, String>
{
    @Override
    public String convertToDatabaseColumn(WeeklyModeEnum attribute)
    {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public WeeklyModeEnum convertToEntityAttribute(String dbData)
    {
        return dbData == null ? null : WeeklyModeEnum.fromCode(dbData);
    }
}
