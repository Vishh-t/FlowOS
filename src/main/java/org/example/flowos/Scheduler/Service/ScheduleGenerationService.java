package org.example.flowos.Scheduler.Service;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Profile.Entity.Profile;
import org.example.flowos.Profile.Repo.ProfileRepo;
import org.example.flowos.Scheduler.Model.TimeAndDayRange;
import org.example.flowos.Scheduler.Record.PlacementResult;
import org.example.flowos.Scheduler.Record.ScheduleGenerationResult;
import org.example.flowos.Task.DTO.FailedOccurrenceDTO;
import org.example.flowos.Task.Embedables.TaskTimeRange;
import org.example.flowos.Task.Entity.Task;
import org.example.flowos.Task.Entity.TaskInstance;
import org.example.flowos.Task.Enums.TaskStatusEnum;
import org.example.flowos.Task.Repository.TaskInstanceRepo;
import org.example.flowos.Task.Repository.TaskRepo;
import org.example.flowos.User.Entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScheduleGenerationService
{
    private final SchedulerService schedulerService;
    private final TaskRepo taskRepo;
    private final ProfileRepo profileRepo;
    private final TaskInstanceRepo taskInstanceRepo;

    @Transactional // whenever a function does more than one db writes ,
    // so due to this either all pass or all fail , this prevents inconsistency across different databases
    public ScheduleGenerationResult generateSchedule(User user, LocalDateTime now)
    {
        List<Task> tasks = taskRepo.findAllByUser(user);
        Profile profile = profileRepo.findById(user.getUserId())
                .orElseThrow(() -> new IllegalStateException("No profile found for user " + user.getUserId()));

        Map<Task, PlacementResult> placements = schedulerService.placeAll(tasks, profile, now);

        List<TaskInstance> savedInstances = new ArrayList<>();
        List<FailedOccurrenceDTO> failedOccurrences = new ArrayList<>();

        for (Map.Entry<Task, PlacementResult> entry : placements.entrySet())
        {
            Task task = entry.getKey();
            PlacementResult result = entry.getValue();

            taskInstanceRepo.deleteAllByTask(task);

            for (TimeAndDayRange slot : result.placedSlots())
            {
                TaskInstance instance = new TaskInstance();
                instance.setTask(task);
                instance.setOccurrenceDay(slot.getStartDay());
                instance.setTime(new TaskTimeRange(slot.getStartTime(), slot.getEndTime()));
                instance.setStatus(TaskStatusEnum.PENDING);
                instance.setTimeOfCompletion(null);

                savedInstances.add(taskInstanceRepo.save(instance));
            }

            for (DayOfWeek failedDay : result.failedDays())
            {
                failedOccurrences.add(new FailedOccurrenceDTO(task.getTaskName(), failedDay));
            }
        }

        return new ScheduleGenerationResult(savedInstances, failedOccurrences);
    }
}