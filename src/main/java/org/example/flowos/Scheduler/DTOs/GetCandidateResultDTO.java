package org.example.flowos.Scheduler.DTOs;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Task.Embedables.Recurrence;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

@Data
@RequiredArgsConstructor
public class GetCandidateResultDTO
{
    GenerateCandidateDTO dto;

    Set<DayOfWeek> excludedDaysOfWeek;


    Recurrence taskRecurrence;

    LocalTime taskStartTime;

    LocalTime latestStartTime;

    int startDayOffset;

    int latestDayOffset;

    int taskDurationInMinutes;

    int incrementalStep;

    int bufferTimeInMinutes;

    int durationToleranceMinutes;

    int commuteTimeInMinutes;

    boolean isBeforeTask;

    boolean isAfterTask;

    boolean isBothWay;


}
