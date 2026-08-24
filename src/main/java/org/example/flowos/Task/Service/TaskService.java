package org.example.flowos.Task.Service;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Exceptions.NotFoundException;
import org.example.flowos.Exceptions.UnauthorizedUserException;
import org.example.flowos.Task.DTO.CreateTaskDTO;
import org.example.flowos.Task.Entity.Task;
import org.example.flowos.Task.Helper.TaskCreationHelpers;
import org.example.flowos.Task.Repository.TaskRepo;
import org.example.flowos.User.Entity.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService
{
    private final TaskRepo repo;

    public Task getTaskByUser(UUID taskId, User user)
    {
        return repo.findByTaskIdAndUser(taskId, user).orElseThrow(() -> new NotFoundException("Task not found"));
    }

    public List<Task> getAllTasksByUser(User user)
    {
        return repo.findAllByUser(user);
    }

    public Task createTask(User user, CreateTaskDTO dto)
    {
        TaskCreationHelpers.CreateTaskResult result = TaskCreationHelpers.createTaskFromDTO(user, dto);
        return repo.save(result.task());
    }

    public Task updateTask(UUID taskId, CreateTaskDTO dto, User user)
    {
        Task DBTask = repo.findByTaskId(taskId);

        if (DBTask == null)
        {
            throw new NotFoundException("Task not found");
        }

        if (!DBTask.getUser().equals(user))
        {
            throw new UnauthorizedUserException("You are not authorized to make this change");
        }

        TaskCreationHelpers.applyDTOToExistingTask(DBTask, dto);
        return repo.save(DBTask);
    }

    public void deleteTask(UUID taskId, User user)
    {
        Task storedTask = repo.findByTaskId(taskId);

        if (storedTask == null)
        {
            throw new NotFoundException("Task not found");
        }

        if (!storedTask.getUser().equals(user))
        {
            throw new UnauthorizedUserException("You are not authorized to perform this action");
        }

        repo.delete(storedTask);
    }
}