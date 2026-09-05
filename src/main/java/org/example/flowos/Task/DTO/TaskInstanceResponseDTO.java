package org.example.flowos.Task.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Task.Enums.TaskStatusEnum;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskInstanceResponseDTO
{
    String taskName;

    LocalDate occurrenceDate;

    LocalTime startTime;

    LocalTime endTime;

    TaskStatusEnum status;

}
