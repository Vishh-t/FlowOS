package org.example.flowos.Task.Enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TaskCategoryConverter implements AttributeConverter<TaskCategoryEnum, String>
{
    @Override
    public String convertToDatabaseColumn(TaskCategoryEnum attribute)
    {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public TaskCategoryEnum convertToEntityAttribute(String dbData)
    {
        return dbData == null ? null : TaskCategoryEnum.fromCode(dbData);
    }
}
