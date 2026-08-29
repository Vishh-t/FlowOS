package org.example.flowos.Task.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleGenerationResponseDTO
{
    Map<DayOfWeek, List<TaskInstanceResponseDTO>> schedule;
    List<FailedOccurrenceDTO> failed;
}