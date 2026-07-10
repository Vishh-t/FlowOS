package org.example.flowos.Scheduler.Helpers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeAndDayRange implements Comparable<TimeAndDayRange> {

    private DayOfWeek startDay;
    private LocalTime startTime;
    private DayOfWeek endDay;
    private LocalTime endTime;

    @Override
    public int compareTo(TimeAndDayRange other) {
        return comparePoints(this.startDay, this.startTime, other.startDay, other.startTime);
    }

    public boolean overlaps(TimeAndDayRange other) {
        boolean thisStartsBeforeOtherEnds =
                comparePoints(this.startDay, this.startTime, other.endDay, other.endTime) < 0;
        boolean otherStartsBeforeThisEnds =
                comparePoints(other.startDay, other.startTime, this.endDay, this.endTime) < 0;
        return thisStartsBeforeOtherEnds && otherStartsBeforeThisEnds;
    }

    public static int comparePoints(DayOfWeek day1, LocalTime time1, DayOfWeek day2, LocalTime time2) {
        int point1 = toMinutesSinceMonday(day1, time1);
        int point2 = toMinutesSinceMonday(day2, time2);
        return Integer.compare(point1, point2);
    }

    private static int toMinutesSinceMonday(DayOfWeek day, LocalTime time) {
        // MONDAY=1 ... SUNDAY=7, so getValue()-1 gives a 0-based day index
        return (day.getValue() - 1) * 1440 + time.toSecondOfDay() / 60;
    }
}
