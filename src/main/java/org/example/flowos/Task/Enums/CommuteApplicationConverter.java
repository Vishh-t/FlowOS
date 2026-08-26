package org.example.flowos.Task.Enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CommuteApplicationConverter implements AttributeConverter<CommuteApplicationEnum, String>
{
    @Override
    public String convertToDatabaseColumn(CommuteApplicationEnum attribute)
    {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public CommuteApplicationEnum convertToEntityAttribute(String dbData)
    {
        return dbData == null ? null : CommuteApplicationEnum.fromCode(dbData);
    }
}
