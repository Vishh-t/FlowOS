package org.example.flowos.Task.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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

    @NotBlank
    String taskName;

    @NotNull
    TaskCategoryEnum category;

    boolean splittable;
    @NotNull
    TaskPriorityEnum priority;

    @NotNull
    FlexibilityEnum movability;

    @Positive
    int durationMinutes;

    @PositiveOrZero
    int toleranceMinutes;

    @PositiveOrZero
    int bufferTimeInMinutes;
    @PositiveOrZero
    int commuteTimeInMinutes;

    @NotNull
    CommuteApplicationEnum commuteApplicableWay;

    LocalDateTime taskDeadline;

    @Valid
    TimeRangeDTO preferredTimeRange;

    @Valid
    @NotNull
    RecurrenceDTO recurrence;
}
