package org.example.flowos.Scheduler.Helpers;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Component
public class GenerateCandidateHelperMethods
{

    public record ShiftedTime(LocalTime time, int dayOffset)
    {
    }

    public static ShiftedTime shift(LocalTime time, int minutesToAdd)
    {
        int totalMinutes = time.toSecondOfDay() / 60 + minutesToAdd;
        int dayOffset = Math.floorDiv(totalMinutes, 1440);
        return new ShiftedTime(time.plusMinutes(minutesToAdd), dayOffset);
    }


    public static int toRawMinutes(int dayOffset, LocalTime time)
    {
        return dayOffset * 1440 + time.toSecondOfDay() / 60;
    }


    public static int daysUntilNextOccurrence(DayOfWeek from, DayOfWeek target)
    {
        return ((target.getValue() - from.getValue()) % 7 + 7) % 7;
    }

}
