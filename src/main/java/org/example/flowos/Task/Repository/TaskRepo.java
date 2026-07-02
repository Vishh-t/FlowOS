package org.example.flowos.Task.Repository;

import org.example.flowos.Task.Entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskRepo extends JpaRepository<Task, UUID>
{
}
