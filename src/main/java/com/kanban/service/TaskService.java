package com.kanban.service;


import com.kanban.mapper.TaskMapper;
import com.kanban.model.Project;
import com.kanban.model.Task;
import com.kanban.model.UserInfo;
import com.kanban.model.dto.TaskDTO;
import com.kanban.model.enums.TaskStatus;
import com.kanban.repository.ProjectRepository;
import com.kanban.repository.TaskRepository;
import com.kanban.repository.UserInfoRepository;
import com.kanban.util.BeanUtilsWrapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
@AllArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserInfoRepository userInfoRepository;
    private final TaskMapper taskMapper;
    private final BeanUtilsWrapper beanUtilsWrapper;

    public Page<TaskDTO> getTasks(String status, Pageable pageable) {
        if (status == null)
            return taskRepository.findAll(pageable).map(taskMapper::taskToDTO);

        if (status.contains(TaskStatus.TO_DO.name()) ||
                status.contains(TaskStatus.IN_PROGRESS.name()) ||
                status.contains(TaskStatus.DONE.name())
        ) {
            return taskRepository.findByStatus(TaskStatus.valueOf(status), pageable)
                    .map(taskMapper::taskToDTO);
        } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    public Optional<Task> getTaskById(Long id) {
        return taskRepository.findById(id);
    }

    public TaskDTO saveNewTask(Task task) {
        if (task.getVersion() == null)
            task.setVersion(1L);
        if (task.getCreatedAt() == null)
            task.setCreatedAt(LocalDateTime.now());
        Task savedTask = taskRepository.save(task);
        return taskMapper.taskToDTO(savedTask);
    }

    public TaskDTO updateTask(Long id, Task newTask) {
        if (!taskRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (newTask.getVersion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        newTask.setUpdatedAt(LocalDateTime.now());
        try {
            Task updatedTask = taskRepository.save(newTask);
            return taskMapper.taskToDTO(updatedTask);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task was updated concurrently");
        }
    }

    @Transactional
    public TaskDTO savePatchedTask(Long id, Task patchedTask) {
        if (patchedTask.getVersion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Task existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        beanUtilsWrapper.copyProperties(patchedTask, existingTask,
                "id", "version", "taskAssignees", "createdAt", "updatedAt");
        existingTask.setUpdatedAt(LocalDateTime.now());

        if (patchedTask.getTaskAssignees() != null) {
            existingTask.getTaskAssignees().clear();
            patchedTask.getTaskAssignees().forEach(taskAssignee -> {
                UserInfo currentUser = userInfoRepository.findById(taskAssignee.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
                existingTask.getTaskAssignees().add(currentUser);
            });
        }

        try {
            Task savedTask = taskRepository.save(existingTask);
            return taskMapper.taskToDTO(savedTask);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task was updated concurrently");
        }
    }

    public void deleteTaskById(Long id) {
        Optional<Task> taskToDeleteOpt = taskRepository.findById(id);
        if (taskToDeleteOpt.isPresent()) {
            Task taskToDelete = taskToDeleteOpt.get();
            Optional<Project> taskProjectOpt = projectRepository.findAll().stream()
                    .filter(project -> project.getTasks().contains(taskToDelete))
                    .findAny();
            if (taskProjectOpt.isPresent()) {
                Project project = taskProjectOpt.get();
                project.getTasks().remove(taskToDelete);
                projectRepository.save(project);
            }
            taskRepository.deleteById(id);
        } else throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
