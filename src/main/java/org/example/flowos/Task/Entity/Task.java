package org.example.flowos.Task.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Task.Embedables.EventOccurrence;
import org.example.flowos.Task.Enums.FlexibilityEnum;
import org.example.flowos.Task.Enums.TaskCategoryEnum;
import org.example.flowos.Task.Enums.TaskPriorityEnum;
import org.example.flowos.User.Entity.User;

import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Task
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID taskId;

    @JoinColumn(name = "userId")
    @ManyToOne
    private User user;

    @NotBlank
    private String taskName;

    private TaskCategoryEnum category;

    private boolean splittable;

    private FlexibilityEnum movability;

    @Embedded
    private EventOccurrence event;

    private TaskPriorityEnum priority;


}
