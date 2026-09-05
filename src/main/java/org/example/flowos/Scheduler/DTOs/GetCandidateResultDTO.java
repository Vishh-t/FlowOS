package org.example.flowos.Scheduler.DTOs;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Scheduler.Helpers.GenerateCandidateHelperMethods.PostPaddingMins;
import org.example.flowos.Task.Embedables.Recurrence;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

@Data
@RequiredArgsConstructor
public class GetCandidateResultDTO
{
    private GenerateCandidateDTO dto;

    private Set<DayOfWeek> excludedDaysOfWeek;


    private Recurrence taskRecurrence;

    private LocalTime taskStartTime;

    private LocalTime latestStartTime;

    private int startDayOffset;

    private int latestDayOffset;

    private int taskDurationInMinutes;

    private int incrementalStep;

    private int bufferTimeInMinutes;

    private int durationToleranceMinutes;

    private int commuteTimeInMinutes;

    private boolean isBeforeTask;

    private boolean isAfterTask;

    private boolean isBothWay;

    private PostPaddingMins postPaddingMins;


}
