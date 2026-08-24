package org.example.flowos.Task.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Task.Enums.CommuteApplicationEnum;
import org.example.flowos.Task.Enums.FlexibilityEnum;
import org.example.flowos.Task.Enums.TaskCategoryEnum;
import org.example.flowos.Task.Enums.TaskPriorityEnum;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTaskDTO
{

    String taskName;
    TaskCategoryEnum category;
    boolean splittable;
    TaskPriorityEnum priority;
    FlexibilityEnum movability;

    int durationMinutes;
    int toleranceMinutes;

    int bufferTimeInMinutes;
    int commuteTimeInMinutes;
    CommuteApplicationEnum commuteApplicableWay;

    LocalDateTime taskDeadline;

    TimeRangeDTO preferredTimeRange;

    RecurrenceDTO recurrence;
}
