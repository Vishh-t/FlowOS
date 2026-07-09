package org.example.flowos.Task.Embedables;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Task.Enums.RecurrenceTypeEnum;
import org.example.flowos.Task.Enums.WeeklyModeEnum;

import java.time.DayOfWeek;
import java.util.Set;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Recurrence
{
    private
    RecurrenceTypeEnum recurrenceTypeEnum;


    private WeeklyModeEnum weeklyMode;
    private Set<DayOfWeek> daysOfWeek;
    private Integer timesPerWeek;


    private Integer dayOfMonth;


    private Integer monthOfYear;

    private Set<DayOfWeek> excludedDaysOfWeek;

}
