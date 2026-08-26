package org.example.flowos.Task.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Task.Enums.TaskStatusEnum;

import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskInstanceResponseDTO
{
    String taskName;

    LocalTime startTime;

    LocalTime endTime;

    TaskStatusEnum status;

}
