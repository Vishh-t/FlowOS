package org.example.flowos.Scheduler.Service;

import org.example.flowos.Scheduler.Helpers.GenerateCandidateHelperMethods.ShiftedTime;
import org.example.flowos.Scheduler.Helpers.TimeAndDayRange;
import org.example.flowos.Scheduler.Service.DTOs.CandidateResult;
import org.example.flowos.Scheduler.Service.DTOs.GenerateCandidateDTO;
import org.example.flowos.Task.Embedables.Recurrence;
import org.example.flowos.Task.Enums.CommuteApplicationEnum;
import org.example.flowos.Task.Enums.WeeklyModeEnum;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.example.flowos.Scheduler.Helpers.GenerateCandidateHelperMethods.*;

public class SchedulerService
{
    Optional<CandidateResult> generateCandidate(GenerateCandidateDTO dto)
    {
        int incrementalStep = 10;

        int taskDurationInMinutes = dto.getTask().getEvent().getTime().getTaskDurationInMinutes();

        boolean isBeforeTask = dto.getTask().getEvent().getCommuteApplicableWay().equals(CommuteApplicationEnum.BEFORE_TASK_ONLY);

        boolean isAfterTask = dto.getTask().getEvent().getCommuteApplicableWay().equals(CommuteApplicationEnum.AFTER_TASK_ONLY);

        boolean isBothWay = dto.getTask().getEvent().getCommuteApplicableWay().equals(CommuteApplicationEnum.BOTH_WAYS);

        int bufferTimeInMinutes = dto.getTask().getEvent().getBufferTimeInMinutes();

        int durationToleranceMinutes = dto.getTask().getEvent().getTime().getDurationToleranceMinutes();

        int commuteTimeInMinutes = dto.getTask().getEvent().getCommuteTimeInMinutes();
        int postPaddingMinutes = bufferTimeInMinutes + durationToleranceMinutes;
        int prePaddingMinutes = 0;

        if (isAfterTask || isBothWay)
        {
            postPaddingMinutes += commuteTimeInMinutes;
        }
        if (isBeforeTask || isBothWay)
        {
            prePaddingMinutes += commuteTimeInMinutes;
        }

        Recurrence taskRecurrence = dto.getTask().getEvent().getTaskRecurrence();
        Set<DayOfWeek> excludedDaysOfWeek = taskRecurrence.getExcludedDaysOfWeek();

        LocalTime baseStart;
        LocalTime baseLatest;

        if (dto.getTask().getEvent().getPreferredTimeRange() != null)
        {
            baseStart = dto.getTask().getEvent().getPreferredTimeRange().getTaskStartTime();
            baseLatest = dto.getTask().getEvent().getPreferredTimeRange().getTaskEndTime();
        } else
        {
            baseStart = dto.getUserProfile().getWakeTime();
            baseLatest = dto.getUserProfile().getSleepTime();
        }


        boolean latestCrossesMidnight = baseLatest.isBefore(baseStart);

        ShiftedTime shiftedStart = shift(baseStart, prePaddingMinutes);
        int startDayOffset = shiftedStart.dayOffset();
        LocalTime startTime = shiftedStart.time();

        ShiftedTime shiftedLatest = shift(baseLatest, -(taskDurationInMinutes + postPaddingMinutes));
        int latestDayOffset = (latestCrossesMidnight ? 1 : 0) + shiftedLatest.dayOffset();
        LocalTime latestStartTime = shiftedLatest.time();

        return getCandidateResult(dto, excludedDaysOfWeek, taskRecurrence, startTime, latestStartTime,
                startDayOffset, latestDayOffset, taskDurationInMinutes, incrementalStep,
                bufferTimeInMinutes, durationToleranceMinutes, commuteTimeInMinutes,
                isBeforeTask, isAfterTask, isBothWay);
    }

    private static Optional<CandidateResult> getCandidateResult(GenerateCandidateDTO dto, Set<DayOfWeek> excludedDaysOfWeek,
                                                                Recurrence taskRecurrence, LocalTime taskStartTime, LocalTime latestStartTime,
                                                                int startDayOffset, int latestDayOffset, int taskDurationInMinutes, int incrementalStep,
                                                                int bufferTimeInMinutes, int durationToleranceMinutes, int commuteTimeInMinutes,
                                                                boolean isBeforeTask, boolean isAfterTask, boolean isBothWay)
    {
        LocalDateTime deadline = dto.getTask().getEvent().getTaskDeadline();
        LocalDateTime now = dto.getNow();

        for (var day : DayOfWeek.values())
        {

            boolean dayExcluded = excludedDaysOfWeek.contains(day);

            boolean notInExactDays = taskRecurrence.getWeeklyMode().equals(WeeklyModeEnum.EXACT_DAYS) && !taskRecurrence.getDaysOfWeek().contains(day);

            if (dayExcluded || notInExactDays)
            {
                continue;
            }


            int effectiveLatestDayOffset = latestDayOffset;
            LocalTime effectiveLatestStartTime = latestStartTime;

            if (deadline != null && now != null)
            {
                int daysUntilAnchor = daysUntilNextOccurrence(now.getDayOfWeek(), day);
                LocalDate anchorDate = now.toLocalDate().plusDays(daysUntilAnchor);

                int deadlineDayOffset = (int) ChronoUnit.DAYS.between(anchorDate, deadline.toLocalDate());
                LocalTime deadlineTime = deadline.toLocalTime();

                if (toRawMinutes(deadlineDayOffset, deadlineTime) < toRawMinutes(startDayOffset, taskStartTime))
                {

                    continue;
                }

                if (toRawMinutes(deadlineDayOffset, deadlineTime) < toRawMinutes(latestDayOffset, latestStartTime))
                {
                    effectiveLatestDayOffset = deadlineDayOffset;
                    effectiveLatestStartTime = deadlineTime;
                }
            }


            int maxSearchDayOffset = startDayOffset + 6;
            LocalTime endOfMaxDay = LocalTime.of(23, 59);
            if (toRawMinutes(effectiveLatestDayOffset, effectiveLatestStartTime) > toRawMinutes(maxSearchDayOffset, endOfMaxDay))
            {
                effectiveLatestDayOffset = maxSearchDayOffset;
                effectiveLatestStartTime = endOfMaxDay;
            }


            int cursorDayOffset = startDayOffset;
            LocalTime cursor = taskStartTime;

            while (toRawMinutes(cursorDayOffset, cursor) <= toRawMinutes(effectiveLatestDayOffset, effectiveLatestStartTime))
            {
                int actualStartDayOffset = cursorDayOffset;
                LocalTime actualCandidateStart = cursor;

                ShiftedTime endShift = shift(cursor, taskDurationInMinutes);
                int actualEndDayOffset = cursorDayOffset + endShift.dayOffset();
                LocalTime actualCandidateEnd = endShift.time();

                int postPaddingMinutes = bufferTimeInMinutes + durationToleranceMinutes;
                if (isAfterTask || isBothWay)
                {
                    postPaddingMinutes += commuteTimeInMinutes;
                }

                ShiftedTime paddedEndShift = shift(actualCandidateEnd, postPaddingMinutes);
                int paddedEndDayOffset = actualEndDayOffset + paddedEndShift.dayOffset();
                LocalTime paddedCandidateEnd = paddedEndShift.time();

                int paddedStartDayOffset = actualStartDayOffset;
                LocalTime paddedCandidateStart = actualCandidateStart;

                if (isBeforeTask || isBothWay)
                {
                    ShiftedTime paddedStartShift = shift(actualCandidateStart, -commuteTimeInMinutes);
                    paddedStartDayOffset = actualStartDayOffset + paddedStartShift.dayOffset();
                    paddedCandidateStart = paddedStartShift.time();
                }

                DayOfWeek actualStartDay = day.plus(actualStartDayOffset);
                DayOfWeek actualEndDay = day.plus(actualEndDayOffset);
                DayOfWeek paddedStartDay = day.plus(paddedStartDayOffset);
                DayOfWeek paddedEndDay = day.plus(paddedEndDayOffset);

                TimeAndDayRange actualCandidate = new TimeAndDayRange(actualStartDay, actualCandidateStart, actualEndDay, actualCandidateEnd);

                TimeAndDayRange paddedCandidate = new TimeAndDayRange(paddedStartDay, paddedCandidateStart, paddedEndDay, paddedCandidateEnd);

                if (dto.getTimeline().isFree(paddedCandidate))
                {
                    return Optional.of(new CandidateResult(actualCandidate, paddedCandidate));
                }

                ShiftedTime next = shift(cursor, incrementalStep);
                cursorDayOffset += next.dayOffset();
                cursor = next.time();
            }

        }

        return Optional.empty();


    }



}
