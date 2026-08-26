package org.example.flowos.Task.Enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RecurrenceTypeConverter implements AttributeConverter<RecurrenceTypeEnum, String>
{
    @Override
    public String convertToDatabaseColumn(RecurrenceTypeEnum attribute)
    {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public RecurrenceTypeEnum convertToEntityAttribute(String dbData)
    {
        return dbData == null ? null : RecurrenceTypeEnum.fromCode(dbData);
    }
}
