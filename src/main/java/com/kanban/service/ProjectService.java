package com.kanban.service;

import com.kanban.mapper.ProjectMapper;
import com.kanban.model.Project;
import com.kanban.model.Task;
import com.kanban.model.UserInfo;
import com.kanban.model.dto.ProjectDTO;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final UserInfoRepository userInfoRepository;
    private final BeanUtilsWrapper beanUtilsWrapper;
    private final ProjectMapper projectMapper;

    public Page<ProjectDTO> getProjects(Pageable pageable) {
        return projectRepository.findAll(pageable).map(projectMapper::projectToDTO);
    }

    public Optional<Project> getProjectById(Long id) {
        return projectRepository.findById(id);
    }

    public ProjectDTO saveNewProject(Project project) {
        if (project.getVersion() == null)
            project.setVersion(1L);
        Project savedProject = projectRepository.save(project);
        return projectMapper.projectToDTO(savedProject);
    }

    public ProjectDTO updateProject(Long id, Project newProject) {
        if (!projectRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (newProject.getVersion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try {
            Project updatedProject = projectRepository.save(newProject);
            return projectMapper.projectToDTO(updatedProject);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project was updated concurrently");
        }
    }

    @Transactional
    public ProjectDTO savePatchedProject(Long id, Project patchedProject) {
        if (patchedProject.getVersion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Project existingProject = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        beanUtilsWrapper.copyProperties(patchedProject, existingProject,
                "id", "version", "tasks", "participants");

        if (!patchedProject.getTasks().isEmpty()) {
            Set<Long> taskIds = patchedProject.getTasks().stream()
                    .map(Task::getId).collect(Collectors.toSet());
            Set<Task> newTasks = new HashSet<>();
            taskIds.forEach(taskId -> {
                Task task = taskRepository.findById(taskId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
                newTasks.add(task);
            });
            existingProject.getTasks().clear();
            existingProject.getTasks().addAll(newTasks);
        }

        if (patchedProject.getParticipants() != null) {
            existingProject.getParticipants().clear();
            patchedProject.getParticipants().forEach(userInfo -> {
                UserInfo currentUser = userInfoRepository.findById(userInfo.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
                existingProject.getParticipants().add(currentUser);
            });
        }

        try {
            Project savedProject = projectRepository.save(existingProject);
            return projectMapper.projectToDTO(savedProject);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project was updated concurrently");
        }
    }

    public void deleteProjectById(Long id) {
        if (!projectRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        projectRepository.deleteById(id);
    }
}
