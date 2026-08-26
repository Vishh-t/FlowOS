package org.example.flowos.Task.Embedables;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter(autoApply = true)
public class DayOfWeekSetConverter implements AttributeConverter<Set<DayOfWeek>, String>
{
    private static final String DELIMITER = ",";

    @Override
    public String convertToDatabaseColumn(Set<DayOfWeek> attribute)
    {
        if (attribute == null) return null;
        if (attribute.isEmpty()) return "";

        return attribute.stream()
                .map(Enum::name)
                .collect(Collectors.joining(DELIMITER));
    }

    @Override
    public Set<DayOfWeek> convertToEntityAttribute(String dbData)
    {
        if (dbData == null) return null;
        if (dbData.isEmpty()) return new LinkedHashSet<>();

        return Arrays.stream(dbData.split(DELIMITER))
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
