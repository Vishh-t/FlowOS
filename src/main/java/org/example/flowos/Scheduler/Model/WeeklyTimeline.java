package org.example.flowos.Scheduler.Model;

import java.util.ArrayList;
import java.util.List;


public class WeeklyTimeline
{
    private  final List<TimeAndDayRange> occupiedSlots = new ArrayList<>();


    public boolean isFree(TimeAndDayRange candidate)
    {
        for (var slot : occupiedSlots)
        {
            if (slot.overlaps(candidate))
            {
                return false;
            }
        }

        return true;
    }

    public boolean occupy(TimeAndDayRange candidate)
    {
        if (!isFree(candidate))
        {
            return false;
        }

        occupiedSlots.add(candidate);
        return true;
    }
}


