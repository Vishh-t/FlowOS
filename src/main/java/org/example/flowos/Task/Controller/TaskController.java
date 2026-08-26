package org.example.flowos.Task.Controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Task.DTO.CreateTaskDTO;
import org.example.flowos.Task.Service.TaskService;
import org.example.flowos.User.Entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tasks")
public class TaskController
{
    private final TaskService service;

    @GetMapping("/{taskId}/getTask")
    public ResponseEntity<?> getTask(@PathVariable UUID taskId, @AuthenticationPrincipal User user)
    {
        return new ResponseEntity<>(service.getTaskByUser(taskId, user), HttpStatus.OK);
    }

    @GetMapping("/getAllTasks")
    public ResponseEntity<?> getAllTasks(@AuthenticationPrincipal User user)
    {
        return new ResponseEntity<>(service.getAllTasksByUser(user), HttpStatus.OK);
    }

    @PostMapping("/createTask")
    public ResponseEntity<?> createTask(@AuthenticationPrincipal User user, @Valid @RequestBody CreateTaskDTO dto)
    {
        return new ResponseEntity<>(service.createTask(user, dto), HttpStatus.CREATED);
    }

    @PutMapping("/{taskId}/updateTask")
    public ResponseEntity<?> updateTask(@AuthenticationPrincipal User user, @Valid @RequestBody CreateTaskDTO dto, @PathVariable UUID taskId)
    {
        return new ResponseEntity<>(service.updateTask(taskId, dto, user), HttpStatus.OK);
    }

    @DeleteMapping("/{taskId}/deleteTask")
    public ResponseEntity<?> deleteTask(@AuthenticationPrincipal User user, @PathVariable UUID taskId)
    {
        service.deleteTask(taskId, user);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
