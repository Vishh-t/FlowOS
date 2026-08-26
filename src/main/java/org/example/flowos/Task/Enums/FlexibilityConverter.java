package org.example.flowos.Task.Enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class FlexibilityConverter implements AttributeConverter<FlexibilityEnum, String>
{
    @Override
    public String convertToDatabaseColumn(FlexibilityEnum attribute)
    {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public FlexibilityEnum convertToEntityAttribute(String dbData)
    {
        return dbData == null ? null : FlexibilityEnum.fromCode(dbData);
    }
}
