package org.example.flowos.Scheduler.Service;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Exceptions.NotFoundException;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ScheduleGenerationService
{
    private final SchedulerService schedulerService;
    private final TaskRepo taskRepo;
    private final ProfileRepo profileRepo;
    private final TaskInstanceRepo taskInstanceRepo;

    // per-user in-memory locks — prevents two overlapping /schedule/generate requests for the
    // SAME user from interleaving their delete/insert cycles. Different users never block each
    // other. Scoped to a single JVM instance — fine for this project's single-Cloud-VM
    // deployment; would need a distributed lock (e.g. Redis) if this ever runs behind a
    // load balancer with multiple app instances.
    private final ConcurrentHashMap<UUID, Object> userLocks = new ConcurrentHashMap<>();

    private Object lockFor(UUID userId)
    {
        return userLocks.computeIfAbsent(userId, id -> new Object());
    }

    @Transactional // whenever a function does more than one db writes ,
    // so due to this either all pass or all fail , this prevents inconsistency across different databases
    public ScheduleGenerationResult generateSchedule(User user)
    {
        synchronized (lockFor(user.getUserId()))
        {
            List<Task> tasks = taskRepo.findAllByUser(user);
            Profile profile = profileRepo.findById(user.getUserId())
                    .orElseThrow(() -> new NotFoundException("No profile found for this user. Please complete onboarding first."));

            LocalDateTime now = LocalDateTime.now(ZoneId.of(profile.getTimezone()));

            Map<Task, PlacementResult> placements = schedulerService.placeAll(tasks, profile, now);

            List<TaskInstance> savedInstances = new ArrayList<>();
            List<FailedOccurrenceDTO> failedOccurrences = new ArrayList<>();

            taskInstanceRepo.deleteAllByTask_User(user);

            for (Map.Entry<Task, PlacementResult> entry : placements.entrySet())
            {
                Task task = entry.getKey();
                PlacementResult result = entry.getValue();


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
}