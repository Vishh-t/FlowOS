package org.example.flowos.Scheduler.Controller;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Task.Helper.ScheduleGeneratorHelperMethods;
import org.example.flowos.Scheduler.Service.ScheduleGenerationService;
import org.example.flowos.Task.DTO.TaskInstanceResponseDTO;
import org.example.flowos.Task.Entity.TaskInstance;
import org.example.flowos.User.Entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class SchedulerController
{
    private final ScheduleGenerationService scheduleGenerationService;

    @PostMapping("/generate")
    public ResponseEntity<?> generateSchedule(@AuthenticationPrincipal User user)
    {
        LocalDateTime now = LocalDateTime.now();

        List<TaskInstance> instances = scheduleGenerationService.generateSchedule(user, now);

        Map<DayOfWeek, List<TaskInstanceResponseDTO>> grouped = instances.stream()
                .collect(Collectors.groupingBy(
                        TaskInstance::getOccurrenceDay,
                        Collectors.mapping(
                                ScheduleGeneratorHelperMethods::fromEntity,
                                Collectors.toList()
                        )
                ));

        return ResponseEntity.ok(grouped);
    }
}