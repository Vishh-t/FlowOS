package org.example.flowos.Scheduler.Helpers;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;


@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeAndDayRange
{
    private DayOfWeek startDay;

    private LocalTime startTime;

    private DayOfWeek endDay;

    private LocalTime endTime;

}
