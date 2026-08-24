package org.example.flowos.Task.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Task.Enums.RecurrenceTypeEnum;
import org.example.flowos.Task.Enums.WeeklyModeEnum;

import java.time.DayOfWeek;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecurrenceDTO
{
    RecurrenceTypeEnum recurrenceTypeEnum;

    WeeklyModeEnum weeklyMode;

    Set<DayOfWeek> daysOfWeek;

    Integer timesPerWeek;

    Set<DayOfWeek> excludedDaysOfWeek;
}
