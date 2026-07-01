package org.example.flowos.Task;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import org.example.flowos.User.User;

import java.util.UUID;

@Entity
public class Task
{
    @Id
    private UUID taskId;

    @JoinColumn(name = "userId")
    @ManyToOne
    private User user;

    @NotBlank
    private String taskName;

    private TaskCategoryEnum Category;

    private boolean splittable;

    

}
