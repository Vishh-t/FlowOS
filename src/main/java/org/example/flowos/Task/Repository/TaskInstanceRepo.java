package org.example.flowos.Task.Repository;

import org.example.flowos.Task.Entity.Task;
import org.example.flowos.Task.Entity.TaskInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TaskInstanceRepo extends JpaRepository<TaskInstance, UUID>
{

    void deleteAllByTask(Task task);
}
