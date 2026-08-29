package org.example.flowos.Scheduler.Helpers;

import org.example.flowos.Task.Embedables.Recurrence;
import org.example.flowos.Task.Enums.RecurrenceTypeEnum;
import org.example.flowos.Task.Enums.WeeklyModeEnum;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Component
public class RecurrenceInterpreters
{
    public  List<DayOfWeek> resolveTargetDays(Recurrence recurrence, LocalDateTime now)
    {
        var excluded = recurrence.getExcludedDaysOfWeek();
        if (excluded == null)
        {
            excluded = new HashSet<>();
        }

        // build "all days minus excluded" once, reused by DAILY and COUNT_ONLY
        List<DayOfWeek> allDays = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values())
        {
            if (!excluded.contains(d))
            {
                allDays.add(d);
            }
        }

        List<DayOfWeek> result = new ArrayList<>();


        RecurrenceTypeEnum type = recurrence.getRecurrenceTypeEnum();

        if (type == RecurrenceTypeEnum.ONE_OFF)
        {
            result.add(now.getDayOfWeek());
        }
        else if (type == RecurrenceTypeEnum.DAILY)
        {
            result = allDays;
        }
        else if (type == RecurrenceTypeEnum.WEEKLY)
        {
            if (WeeklyModeEnum.EXACT_DAYS == recurrence.getWeeklyMode())
            {
                for (DayOfWeek d : recurrence.getDaysOfWeek())
                {
                    if (!excluded.contains(d))
                    {
                        result.add(d);
                    }
                }
            }
            else if (recurrence.getWeeklyMode() == WeeklyModeEnum.COUNT_ONLY)
            {
                result = pickSpreadDays(allDays, recurrence.getTimesPerWeek());
            }
        }
        else
        {
            // MONTHLY / ANNUALLY not implemented yet
            throw new UnsupportedOperationException("Recurrence type not yet supported: " + type);
        }

        return result;
    }

    private List<DayOfWeek> pickSpreadDays(List<DayOfWeek> availableDays, int timesPerWeek)
    {
        List<DayOfWeek> result = new ArrayList<>();

        if (availableDays.isEmpty())
        {
            return result; // nothing possible, e.g. all days excluded
        }

        for (int i = 0; i < timesPerWeek; i++)
        {
            int index = (i * availableDays.size()) / timesPerWeek;
            result.add(availableDays.get(index));
        }

        return result;
    }

    public List<DayOfWeek> getAvailableDays(Recurrence recurrence)
    {
        Set<DayOfWeek> excluded = recurrence.getExcludedDaysOfWeek();
        if (excluded == null) { excluded = new HashSet<>(); }

        List<DayOfWeek> allDays = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values())
        {
            if (!excluded.contains(d)) { allDays.add(d); }
        }
        return allDays;
    }
}
