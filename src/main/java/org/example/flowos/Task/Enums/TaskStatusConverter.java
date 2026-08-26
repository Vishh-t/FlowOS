package org.example.flowos.Task.Enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TaskStatusConverter implements AttributeConverter<TaskStatusEnum, String>
{
    @Override
    public String convertToDatabaseColumn(TaskStatusEnum attribute)
    {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public TaskStatusEnum convertToEntityAttribute(String dbData)
    {
        return dbData == null ? null : TaskStatusEnum.fromCode(dbData);
    }
}
