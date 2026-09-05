package org.example.flowos.Task.Repository;

import org.example.flowos.Task.Entity.Task;
import org.example.flowos.Task.Entity.TaskInstance;
import org.example.flowos.User.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskInstanceRepo extends JpaRepository<TaskInstance, UUID>
{

    void deleteAllByTask(Task task);

    void deleteAllByTask_User(User user);

    List<TaskInstance> findAllByTask_User(User user);
}
