package org.example.flowos.Scheduler.Service;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Profile.Entity.Profile;
import org.example.flowos.Scheduler.DTOs.PlaceTaskDTO;
import org.example.flowos.Scheduler.Helpers.GenerateCandidateHelperMethods.ShiftedTime;
import org.example.flowos.Scheduler.Helpers.PriorityInterpreter;
import org.example.flowos.Scheduler.Helpers.RecurrenceInterpreters;
import org.example.flowos.Scheduler.Model.TimeAndDayRange;
import org.example.flowos.Scheduler.Record.CandidateResult;
import org.example.flowos.Scheduler.DTOs.GenerateCandidateDTO;
import org.example.flowos.Scheduler.DTOs.GetCandidateResultDTO;
import org.example.flowos.Scheduler.Model.WeeklyTimeline;
import org.example.flowos.Scheduler.Record.PlacementResult;
import org.example.flowos.Task.Embedables.Recurrence;
import org.example.flowos.Task.Entity.Task;
import org.example.flowos.Task.Enums.CommuteApplicationEnum;
import org.example.flowos.Task.Enums.RecurrenceTypeEnum;
import org.example.flowos.Task.Enums.WeeklyModeEnum;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.example.flowos.Scheduler.Helpers.GenerateCandidateHelperMethods.*;

@Service
@RequiredArgsConstructor
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

        GetCandidateResultDTO resultDto = generateGetCandidateResultDTO(dto, excludedDaysOfWeek, taskRecurrence, isBothWay, isAfterTask, isBeforeTask, startTime, latestStartTime, bufferTimeInMinutes, startDayOffset, latestDayOffset, taskDurationInMinutes, incrementalStep, durationToleranceMinutes, commuteTimeInMinutes);

        return getCandidateResult(resultDto
        );
    }

    private static GetCandidateResultDTO generateGetCandidateResultDTO(GenerateCandidateDTO dto, Set<DayOfWeek> excludedDaysOfWeek, Recurrence taskRecurrence, boolean isBothWay, boolean isAfterTask, boolean isBeforeTask, LocalTime startTime, LocalTime latestStartTime, int bufferTimeInMinutes, int startDayOffset, int latestDayOffset, int taskDurationInMinutes, int incrementalStep, int durationToleranceMinutes, int commuteTimeInMinutes)
    {
        GetCandidateResultDTO resultDto = new GetCandidateResultDTO();

        resultDto.setDto(dto);
        resultDto.setExcludedDaysOfWeek(excludedDaysOfWeek);
        resultDto.setTaskRecurrence(taskRecurrence);
        resultDto.setBothWay(isBothWay);
        resultDto.setAfterTask(isAfterTask);
        resultDto.setBeforeTask(isBeforeTask);
        resultDto.setTaskStartTime(startTime);
        resultDto.setLatestStartTime(latestStartTime);
        resultDto.setBufferTimeInMinutes(bufferTimeInMinutes);
        resultDto.setStartDayOffset(startDayOffset);
        resultDto.setLatestDayOffset(latestDayOffset);
        resultDto.setTaskDurationInMinutes(taskDurationInMinutes);
        resultDto.setIncrementalStep(incrementalStep);
        resultDto.setDurationToleranceMinutes(durationToleranceMinutes);
        resultDto.setCommuteTimeInMinutes(commuteTimeInMinutes);
        return resultDto;
    }


    private static Optional<CandidateResult> getCandidateResult(GetCandidateResultDTO resultDto)
    {
        LocalDateTime deadline = resultDto.getDto().getTask().getEvent().getTaskDeadline();
        LocalDateTime now = resultDto.getDto().getNow();

        DayOfWeek day = resultDto.getDto().getTargetDay();


        int effectiveLatestDayOffset = resultDto.getLatestDayOffset();
        LocalTime effectiveLatestStartTime = resultDto.getLatestStartTime();

        if (deadline != null && now != null)
        {
            int daysUntilAnchor = daysUntilNextOccurrence(now.getDayOfWeek(), day);
            LocalDate anchorDate = now.toLocalDate().plusDays(daysUntilAnchor);

            int deadlineDayOffset = (int) ChronoUnit.DAYS.between(anchorDate, deadline.toLocalDate());
            LocalTime deadlineTime = deadline.toLocalTime();

            if (toRawMinutes(deadlineDayOffset, deadlineTime) < toRawMinutes(resultDto.getStartDayOffset(), resultDto.getTaskStartTime()))
            {

                return Optional.empty();
            }

            if (toRawMinutes(deadlineDayOffset, deadlineTime) < toRawMinutes(resultDto.getLatestDayOffset(), resultDto.getLatestStartTime()))
            {
                effectiveLatestDayOffset = deadlineDayOffset;
                effectiveLatestStartTime = deadlineTime;
            }
        }


        int maxSearchDayOffset = resultDto.getStartDayOffset() + 6;
        LocalTime endOfMaxDay = LocalTime.of(23, 59);
        if (toRawMinutes(effectiveLatestDayOffset, effectiveLatestStartTime) > toRawMinutes(maxSearchDayOffset, endOfMaxDay))
        {
            effectiveLatestDayOffset = maxSearchDayOffset;
            effectiveLatestStartTime = endOfMaxDay;
        }


        int cursorDayOffset = resultDto.getStartDayOffset();
        LocalTime cursor = resultDto.getTaskStartTime();

        while (toRawMinutes(cursorDayOffset, cursor) <= toRawMinutes(effectiveLatestDayOffset, effectiveLatestStartTime))
        {
            int actualStartDayOffset = cursorDayOffset;
            LocalTime actualCandidateStart = cursor;

            ShiftedTime endShift = shift(cursor, resultDto.getTaskDurationInMinutes());
            int actualEndDayOffset = cursorDayOffset + endShift.dayOffset();
            LocalTime actualCandidateEnd = endShift.time();

            int postPaddingMinutes = resultDto.getBufferTimeInMinutes() + resultDto.getDurationToleranceMinutes();
            if (resultDto.isAfterTask() || resultDto.isBothWay())
            {
                postPaddingMinutes += resultDto.getCommuteTimeInMinutes();
            }

            ShiftedTime paddedEndShift = shift(actualCandidateEnd, postPaddingMinutes);
            int paddedEndDayOffset = actualEndDayOffset + paddedEndShift.dayOffset();
            LocalTime paddedCandidateEnd = paddedEndShift.time();

            int paddedStartDayOffset = actualStartDayOffset;
            LocalTime paddedCandidateStart = actualCandidateStart;

            if (resultDto.isBeforeTask() || resultDto.isBothWay())
            {
                ShiftedTime paddedStartShift = shift(actualCandidateStart, -resultDto.getCommuteTimeInMinutes());
                paddedStartDayOffset = actualStartDayOffset + paddedStartShift.dayOffset();
                paddedCandidateStart = paddedStartShift.time();
            }

            DayOfWeek actualStartDay = day.plus(actualStartDayOffset);
            DayOfWeek actualEndDay = day.plus(actualEndDayOffset);
            DayOfWeek paddedStartDay = day.plus(paddedStartDayOffset);
            DayOfWeek paddedEndDay = day.plus(paddedEndDayOffset);

            TimeAndDayRange actualCandidate = new TimeAndDayRange(actualStartDay, actualCandidateStart, actualEndDay, actualCandidateEnd);

            TimeAndDayRange paddedCandidate = new TimeAndDayRange(paddedStartDay, paddedCandidateStart, paddedEndDay, paddedCandidateEnd);

            if (resultDto.getDto().getTimeline().isFree(paddedCandidate))
            {
                return Optional.of(new CandidateResult(actualCandidate, paddedCandidate));
            }

            ShiftedTime next = shift(cursor, resultDto.getIncrementalStep());
            cursorDayOffset += next.dayOffset();
            cursor = next.time();
        }


        return Optional.empty();


    }

    private final RecurrenceInterpreters recurrenceInterpreters ;

    public PlacementResult placeTask(PlaceTaskDTO placementDto)
    {
        Recurrence recurrence = placementDto.getTask().getEvent().getTaskRecurrence();
        List<DayOfWeek> targetDays = recurrenceInterpreters.resolveTargetDays(recurrence, placementDto.getNow());

        boolean allowFallback = recurrence.getRecurrenceTypeEnum() == RecurrenceTypeEnum.WEEKLY
                && recurrence.getWeeklyMode() == WeeklyModeEnum.COUNT_ONLY;

        List<DayOfWeek> fallbackPool = allowFallback
                ? recurrenceInterpreters.getAvailableDays(recurrence)
                : List.of();

        // every target day is "reserved" upfront so one occurrence's fallback can't steal another's day
        Set<DayOfWeek> claimedDays = new HashSet<>(targetDays);

        List<TimeAndDayRange> placedSlots = new ArrayList<>();
        List<DayOfWeek> failedDays = new ArrayList<>();

        for (DayOfWeek originalDay : targetDays)
        {
            DayOfWeek candidateDay = originalDay;
            boolean placed = false;

            while (true)
            {
                GenerateCandidateDTO dto = new GenerateCandidateDTO(
                        placementDto.getTask(), placementDto.getProfile(),
                        placementDto.getTimeline(), placementDto.getNow(), candidateDay);

                Optional<CandidateResult> result = generateCandidate(dto);

                if (result.isPresent())
                {
                    placementDto.getTimeline().occupy(result.get().paddedRange());
                    placedSlots.add(result.get().actualRange());
                    claimedDays.add(candidateDay);
                    placed = true;
                    break;
                }

                if (!allowFallback) { break; }

                Optional<DayOfWeek> next = fallbackPool.stream()
                        .filter(d -> !claimedDays.contains(d))
                        .findFirst();

                if (next.isEmpty()) { break; } // no untried, unclaimed day left

                candidateDay = next.get();
                claimedDays.add(candidateDay); // mark as attempted so it won't be retried by another occurrence
            }

            if (!placed) { failedDays.add(originalDay); }
        }

        return new PlacementResult(targetDays.size(), placedSlots.size(), placedSlots, failedDays);
    }

    private final PriorityInterpreter priorityInterpreter ;

    public Map<Task, PlacementResult> placeAll(List<Task> tasks, Profile profile, LocalDateTime now)
    {
        WeeklyTimeline timeline = new WeeklyTimeline();
        List<Task> sortedTasks = priorityInterpreter.sortForPlacement(tasks);

        Map<Task, PlacementResult> results = new LinkedHashMap<>();

        for (Task task : sortedTasks)
        {
            PlaceTaskDTO dto = new PlaceTaskDTO(task, profile, timeline, now);
            PlacementResult result = placeTask(dto);
            results.put(task, result);
        }

        return results;
    }


}
