package org.example.flowos.Scheduler.Record;

import org.example.flowos.Scheduler.Helpers.TimeAndDayRange;

import java.time.DayOfWeek;
import java.util.List;

public record PlacementResult(int requested, int placed, List<TimeAndDayRange> placedSlots, List<DayOfWeek> failedDays)
{


}
