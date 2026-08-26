package org.example.flowos.Task.DTO;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
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
    @NotNull
    RecurrenceTypeEnum recurrenceTypeEnum;

    @NotNull
    WeeklyModeEnum weeklyMode;


    Set<DayOfWeek> daysOfWeek;


    Integer timesPerWeek;


    Set<DayOfWeek> excludedDaysOfWeek;

    @AssertTrue(message = "daysOfWeek is required when weeklyMode is EXACT_DAYS")
    public boolean isDaysOfWeekValid()
    {
        if (weeklyMode == WeeklyModeEnum.EXACT_DAYS)
        {
            return daysOfWeek != null && !daysOfWeek.isEmpty();
        }
        return true;
    }

    @AssertTrue(message = "timesPerWeek is required when weeklyMode is COUNT_ONLY")
    public boolean isTimesPerWeekValid()
    {
        if (weeklyMode == WeeklyModeEnum.COUNT_ONLY)
        {
            return timesPerWeek != null && timesPerWeek > 0;
        }
        return true;
    }
}
