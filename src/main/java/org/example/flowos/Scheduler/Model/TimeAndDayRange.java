package org.example.flowos.Scheduler.Model;

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

    private static final int MINUTES_PER_WEEK = 7 * 1440; // 10080

    @Override
    public int compareTo(TimeAndDayRange other) {
        return comparePoints(this.startDay, this.startTime, other.startDay, other.startTime);
    }

    public boolean overlaps(TimeAndDayRange other) {
        int thisStart = this.startMinutes();
        int thisEnd = this.endMinutesNormalized();

        int otherStart = other.startMinutes();
        int otherEnd = other.endMinutesNormalized();

        for (int shift : new int[]{-MINUTES_PER_WEEK, 0, MINUTES_PER_WEEK}) {
            int shiftedOtherStart = otherStart + shift;
            int shiftedOtherEnd = otherEnd + shift;
            boolean overlapsAtThisShift = thisStart < shiftedOtherEnd && shiftedOtherStart < thisEnd;
            if (overlapsAtThisShift) {
                return true;
            }
        }
        return false;
    }

    public static int comparePoints(DayOfWeek day1, LocalTime time1, DayOfWeek day2, LocalTime time2) {
        int point1 = toMinutesSinceMonday(day1, time1);
        int point2 = toMinutesSinceMonday(day2, time2);
        return Integer.compare(point1, point2);
    }

    private int startMinutes() {
        return toMinutesSinceMonday(startDay, startTime);
    }

    private int endMinutesNormalized() {
        int start = startMinutes();
        int end = toMinutesSinceMonday(endDay, endTime);
        return (end <= start) ? end + MINUTES_PER_WEEK : end;
    }

    private static int toMinutesSinceMonday(DayOfWeek day, LocalTime time) {
        // MONDAY=1 ... SUNDAY=7, so getValue()-1 gives a 0-based day index
        return (day.getValue() - 1) * 1440 + time.toSecondOfDay() / 60;
    }
}
