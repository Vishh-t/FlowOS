package org.example.flowos.Scheduler.Service;

import org.example.flowos.Scheduler.Helpers.TimeAndDayRange;
import org.example.flowos.Scheduler.Service.DTOs.CandidateResult;
import org.example.flowos.Scheduler.Service.DTOs.GenerateCandidateDTO;
import org.example.flowos.Task.Embedables.Recurrence;
import org.example.flowos.Task.Enums.CommuteApplicationEnum;
import org.example.flowos.Task.Enums.WeeklyModeEnum;

import java.time.DayOfWeek;
import java.time.LocalTime;
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
        LocalTime startTime;
        LocalTime latestStartTime;

        if (dto.getTask().getEvent().getPreferredTimeRange() != null)
        {
            startTime = dto.getTask().getEvent().getPreferredTimeRange().getTaskStartTime().plusMinutes(prePaddingMinutes);
            latestStartTime = dto.getTask().getEvent().getPreferredTimeRange().getTaskEndTime()
                    .minusMinutes(taskDurationInMinutes + postPaddingMinutes);
        } else
        {
            startTime = dto.getUserProfile().getWakeTime().plusMinutes(prePaddingMinutes);
            latestStartTime = dto.getUserProfile().getSleepTime()
                    .minusMinutes(taskDurationInMinutes + postPaddingMinutes);
        }
        return getCandidateResult(dto, excludedDaysOfWeek, taskRecurrence, startTime, latestStartTime, taskDurationInMinutes, incrementalStep, isBeforeTask, isAfterTask, isBothWay);
    }

    private static Optional<CandidateResult> getCandidateResult(GenerateCandidateDTO dto, Set<DayOfWeek> excludedDaysOfWeek, Recurrence taskRecurrence, LocalTime taskStartTime, LocalTime latestStartTime, int taskDurationInMinutes, int incrementalStep, boolean isBeforeTask, boolean isAfterTask, boolean isBothWay)
    {
        for (var day : DayOfWeek.values())
        {

            boolean dayExcluded = excludedDaysOfWeek.contains(day);

            boolean notInExactDays = dto.getTask().getEvent().getTaskRecurrence().getWeeklyMode().equals(WeeklyModeEnum.EXACT_DAYS) && !taskRecurrence.getDaysOfWeek().contains(day);

            if (dayExcluded || notInExactDays)
            {
                continue;
            }

            LocalTime cursor = taskStartTime;

            // here we are still missing the deadline check and the midnight thing
            while (!cursor.isAfter(latestStartTime))
            {
                LocalTime actualCandidateStart = cursor;

                LocalTime actualCandidateEnd = cursor.plusMinutes(taskDurationInMinutes);

                LocalTime paddedCandidateEnd;

                LocalTime paddedCandidateStart = actualCandidateStart;

                paddedCandidateEnd = addBufferTime(dto, actualCandidateEnd);

                int durationToleranceMinutes = dto.getTask().getEvent().getTime().getDurationToleranceMinutes();

                paddedCandidateEnd = addTolerance(paddedCandidateEnd, durationToleranceMinutes);

                if (isBeforeTask)
                {
                    paddedCandidateStart = subtractCommuteTime(dto, actualCandidateStart);

                } else if (isAfterTask)
                {
                    paddedCandidateEnd = addCommuteTime(dto, paddedCandidateEnd);


                } else if (isBothWay)
                {
                    paddedCandidateStart = subtractCommuteTime(dto, actualCandidateStart);

                    paddedCandidateEnd = addCommuteTime(dto, paddedCandidateEnd);

                }

                TimeAndDayRange actualCandidate = new TimeAndDayRange(day, actualCandidateStart, day, actualCandidateEnd);

                TimeAndDayRange paddedCandidate = new TimeAndDayRange(day, paddedCandidateStart, day, paddedCandidateEnd);

                if (dto.getTimeline().isFree(paddedCandidate))
                {
                    return Optional.of(new CandidateResult(actualCandidate, paddedCandidate));
                }

                cursor = cursor.plusMinutes(incrementalStep);
            }

        }

        return Optional.empty();
    }


}
