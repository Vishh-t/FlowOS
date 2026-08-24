package org.example.flowos.Task.Repository;

import org.example.flowos.Task.Entity.Task;
import org.example.flowos.User.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepo extends JpaRepository<Task, UUID>
{
    Optional<Task> findByTaskIdAndUser(UUID taskId, User user);

    List<Task> findAllByUser(User user);


    Task findByTaskId(UUID givenTaskId);
}
