package org.example.flowos.Scheduler.Record;

import org.example.flowos.Task.DTO.FailedOccurrenceDTO;
import org.example.flowos.Task.Entity.TaskInstance;

import java.util.List;

public record ScheduleGenerationResult(List<TaskInstance> placedInstances, List<FailedOccurrenceDTO> failedOccurrences) {}