package org.example.flowos.Task.Helper;

import org.example.flowos.Task.DTO.TaskInstanceResponseDTO;
import org.example.flowos.Task.Entity.TaskInstance;



public class ScheduleGeneratorHelperMethods
{
    public static TaskInstanceResponseDTO fromEntity(TaskInstance instance)
    {
        return new TaskInstanceResponseDTO(
                instance.getTask().getTaskName(),
                instance.getTime().getTaskStartTime(),
                instance.getTime().getTaskEndTime(),
                instance.getStatus()
        );
    }
}
