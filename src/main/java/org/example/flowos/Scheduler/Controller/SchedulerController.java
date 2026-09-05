package org.example.flowos.Scheduler.Controller;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Scheduler.Record.ScheduleGenerationResult;
import org.example.flowos.Task.DTO.ScheduleGenerationResponseDTO;
import org.example.flowos.Task.Helper.ScheduleGeneratorHelperMethods;
import org.example.flowos.Scheduler.Service.ScheduleGenerationService;
import org.example.flowos.Task.DTO.TaskInstanceResponseDTO;
import org.example.flowos.Task.Entity.TaskInstance;
import org.example.flowos.Task.Repository.TaskInstanceRepo;
import org.example.flowos.User.Entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class SchedulerController
{
    private final ScheduleGenerationService scheduleGenerationService;
    private final TaskInstanceRepo taskInstanceRepo;

    @PostMapping("/generate")
    public ResponseEntity<?> generateSchedule(@AuthenticationPrincipal User user)
    {
        ScheduleGenerationResult result = scheduleGenerationService.generateSchedule(user);

        Map<DayOfWeek, List<TaskInstanceResponseDTO>> grouped = result.placedInstances().stream()
                .collect(Collectors.groupingBy(
                        instance -> instance.getOccurrenceDate().getDayOfWeek(),
                        Collectors.mapping(ScheduleGeneratorHelperMethods::fromEntity, Collectors.toList())
                ));

        ScheduleGenerationResponseDTO response = new ScheduleGenerationResponseDTO(grouped, result.failedOccurrences());

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<?> getSchedule(@AuthenticationPrincipal User user)
    {
        List<TaskInstance> instances = taskInstanceRepo.findAllByTask_User(user);

        Map<DayOfWeek, List<TaskInstanceResponseDTO>> grouped = instances.stream()
                .collect(Collectors.groupingBy(
                        instance -> instance.getOccurrenceDate().getDayOfWeek(),
                        Collectors.mapping(ScheduleGeneratorHelperMethods::fromEntity, Collectors.toList())
                ));

        return ResponseEntity.ok(grouped);
    }
}