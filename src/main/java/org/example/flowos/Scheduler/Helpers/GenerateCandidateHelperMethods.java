package org.example.flowos.Scheduler.Helpers;

import java.time.LocalTime;

public class GenerateCandidateHelperMethods
{

    public record ShiftedTime(LocalTime time, int dayOffset) {}

    public static ShiftedTime shift(LocalTime time, int minutesToAdd)
    {
        int totalMinutes = time.toSecondOfDay() / 60 + minutesToAdd;
        int dayOffset = Math.floorDiv(totalMinutes, 1440);
        return new ShiftedTime(time.plusMinutes(minutesToAdd), dayOffset);
    }
}
