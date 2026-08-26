package org.example.flowos.Task.Enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TaskPriorityConverter implements AttributeConverter<TaskPriorityEnum, String>
{
    @Override
    public String convertToDatabaseColumn(TaskPriorityEnum attribute)
    {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public TaskPriorityEnum convertToEntityAttribute(String dbData)
    {
        return dbData == null ? null : TaskPriorityEnum.fromCode(dbData);
    }
}
