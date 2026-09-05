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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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
    // other. Scoped to a single JVM instance.
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

            // Anchor date for this generation run: the Monday of "this" calendar week, relative
            // to now. previousOrSame means if `now` itself falls on a Monday, that same date is
            // used as the anchor rather than jumping back a full week.
            LocalDate anchorMonday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

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
                    // Translate the abstract DayOfWeek the Scheduler placed this on into a real
                    // calendar date, anchored to this run's Monday. This is the ONLY place a
                    // DayOfWeek slot becomes a real date — SchedulerService/generateCandidate/
                    // TimeAndDayRange all stay entirely DayOfWeek-based, untouched.
                    LocalDate actualDate = anchorMonday.plusDays(slot.getStartDay().getValue() - 1);

                    // If that weekday has already passed this calendar week (e.g. now is
                    // Wednesday and this slot is Monday), tagging it with the already-past date
                    // would silently create a TaskInstance dated in the past. Roll it forward to
                    // next week's occurrence of the same weekday instead. Doesn't touch placement
                    // logic at all — generateCandidate/TimeAndDayRange never see this, it's purely
                    // which calendar date this already-placed slot gets tagged with.
                    if (actualDate.isBefore(now.toLocalDate()))
                    {
                        actualDate = actualDate.plusDays(7);
                    }

                    TaskInstance instance = new TaskInstance();
                    instance.setTask(task);
                    instance.setOccurrenceDate(actualDate);
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