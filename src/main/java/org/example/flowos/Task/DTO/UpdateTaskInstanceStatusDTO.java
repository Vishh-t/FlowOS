package org.example.flowos.Task.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Task.Enums.TaskStatusEnum;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTaskInstanceStatusDTO
{
    @NotNull
    TaskStatusEnum status;
}
