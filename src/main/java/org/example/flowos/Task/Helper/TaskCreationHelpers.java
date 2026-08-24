package org.example.flowos.Task.Helper;

import org.example.flowos.Task.DTO.CreateTaskDTO;
import org.example.flowos.Task.Embedables.EventOccurrence;
import org.example.flowos.Task.Embedables.EventTime;
import org.example.flowos.Task.Embedables.Recurrence;
import org.example.flowos.Task.Embedables.TaskTimeRange;
import org.example.flowos.Task.Entity.Task;
import org.example.flowos.User.Entity.User;

public class TaskCreationHelpers
{
    public static CreateTaskResult createTaskFromDTO(User user, CreateTaskDTO dto)
    {
        Task task = new Task();
        task.setUser(user);

        EventOccurrence event = new EventOccurrence();
        applyDTOToTaskAndEvent(task, event, dto);

        task.setEvent(event);

        CreateTaskResult result = new CreateTaskResult(task, event);
        return result;
    }

    public static void applyDTOToExistingTask(Task existingTask, CreateTaskDTO dto)
    {
        EventOccurrence event = existingTask.getEvent();
        applyDTOToTaskAndEvent(existingTask, event, dto);
        // event's status/allottedTimeRange are untouched since we reused the existing EventOccurrence
    }

    private static void applyDTOToTaskAndEvent(Task task, EventOccurrence event, CreateTaskDTO dto)
    {
        task.setTaskName(dto.getTaskName());
        task.setCategory(dto.getCategory());
        task.setSplittable(dto.isSplittable());
        task.setPriority(dto.getPriority());
        task.setMovability(dto.getMovability());

        event.setTime(new EventTime(dto.getDurationMinutes(), dto.getToleranceMinutes()));
        event.setBufferTimeInMinutes(dto.getBufferTimeInMinutes());
        event.setCommuteTimeInMinutes(dto.getCommuteTimeInMinutes());
        event.setCommuteApplicableWay(dto.getCommuteApplicableWay());
        event.setTaskDeadline(dto.getTaskDeadline());

        if (dto.getPreferredTimeRange() != null)
        {
            TaskTimeRange range = new TaskTimeRange(
                    dto.getPreferredTimeRange().getTaskStartTime(),
                    dto.getPreferredTimeRange().getTaskEndTime());
            event.setPreferredTimeRange(range);
        }
        else
        {
            event.setPreferredTimeRange(null); // explicit clear, in case an update removes a previously-set range
        }

        if (dto.getRecurrence() != null)
        {
            Recurrence recurrence = new Recurrence();
            recurrence.setRecurrenceTypeEnum(dto.getRecurrence().getRecurrenceTypeEnum());
            recurrence.setWeeklyMode(dto.getRecurrence().getWeeklyMode());
            recurrence.setDaysOfWeek(dto.getRecurrence().getDaysOfWeek());
            recurrence.setTimesPerWeek(dto.getRecurrence().getTimesPerWeek());
            recurrence.setExcludedDaysOfWeek(dto.getRecurrence().getExcludedDaysOfWeek());
            event.setTaskRecurrence(recurrence);
        }
    }

    public record CreateTaskResult(Task task, EventOccurrence event)
    {
    }
}