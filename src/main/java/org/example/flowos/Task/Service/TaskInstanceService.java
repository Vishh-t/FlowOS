package org.example.flowos.Task.Service;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Exceptions.NotFoundException;
import org.example.flowos.Exceptions.UnauthorizedUserException;
import org.example.flowos.Profile.Entity.Profile;
import org.example.flowos.Profile.Repo.ProfileRepo;
import org.example.flowos.Task.Entity.TaskInstance;
import org.example.flowos.Task.Enums.TaskStatusEnum;
import org.example.flowos.Task.Repository.TaskInstanceRepo;
import org.example.flowos.User.Entity.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskInstanceService
{
    private final TaskInstanceRepo repo;
    private final ProfileRepo profileRepo;

    public TaskInstance updateStatus(UUID instanceId, TaskStatusEnum newStatus, User user)
    {
        TaskInstance instance = repo.findById(instanceId)
                .orElseThrow(() -> new NotFoundException("Task instance not found"));

        if (!instance.getTask().getUser().equals(user))
        {
            throw new UnauthorizedUserException("You are not authorized to modify this task instance");
        }

        Profile profile = profileRepo.findById(user.getUserId())
                .orElseThrow(() -> new NotFoundException("No profile found for this user. Please complete onboarding first."));

        instance.setStatus(newStatus);

        // clears any stale completion timestamp if a DONE instance is reverted to another status
        instance.setTimeOfCompletion(newStatus == TaskStatusEnum.DONE
                ? LocalDateTime.now(ZoneId.of(profile.getTimezone()))
                : null);

        return repo.save(instance);
    }
}
