package org.example.flowos.Task.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Task.DTO.UpdateTaskInstanceStatusDTO;
import org.example.flowos.Task.Service.TaskInstanceService;
import org.example.flowos.User.Entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/instances")
public class TaskInstanceController
{
    private final TaskInstanceService service;

    @PatchMapping("/{instanceId}/status")
    public ResponseEntity<?> updateStatus(@PathVariable UUID instanceId,
                                           @Valid @RequestBody UpdateTaskInstanceStatusDTO dto,
                                           @AuthenticationPrincipal User user)
    {
        return new ResponseEntity<>(service.updateStatus(instanceId, dto.getStatus(), user), HttpStatus.OK);
    }
}
