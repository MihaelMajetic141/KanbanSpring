package com.kanban.service;


import com.kanban.mapper.ProjectMapper;
import com.kanban.model.Project;
import com.kanban.model.Task;
import com.kanban.model.UserInfo;
import com.kanban.model.dto.ProjectDTO;
import com.kanban.model.enums.TaskPriority;
import com.kanban.model.enums.TaskStatus;
import com.kanban.repository.ProjectRepository;
import com.kanban.repository.TaskRepository;
import com.kanban.repository.UserInfoRepository;
import com.kanban.util.BeanUtilsWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProjectServiceTests {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private BeanUtilsWrapper beanUtilsWrapper;

    @InjectMocks
    private ProjectService projectService;

    private Project project;
    private ProjectDTO projectDTO;
    private Project patchedProject;
    private UserInfo userInfo;
    private Task task;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(1L)
                .version(1L)
                .name("Test Project")
                .participants(new HashSet<>())
                .tasks(new HashSet<>())
                .build();

        projectDTO = ProjectDTO.builder()
                .id(1L)
                .name("Test Project")
                .participantIds(new ArrayList<>())
                .taskIds(new ArrayList<>())
                .build();

        patchedProject = Project.builder()
                .name("Patched Project")
                .version(1L)
                .participants(new HashSet<>())
                .tasks(new HashSet<>())
                .build();

        userInfo = UserInfo.builder()
                .id(2L)
                .username("test_user")
                .build();
        patchedProject.getParticipants().add(userInfo);

        task = Task.builder()
                .id(1L)
                .title("Test Task")
                .description("This is a test task description")
                .status(TaskStatus.TO_DO)
                .priority(TaskPriority.MEDIUM)
                .dueAt(LocalDateTime.now().plusDays(7))
                .taskAssignees(new HashSet<>())
                .build();
        patchedProject.getTasks().add(task);

        pageable = PageRequest.of(0, 10);
    }

    @Test
    void getAllProjects_Success() {
        Page<Project> projectsPage = new PageImpl<>(List.of(project));
        when(projectRepository.findAll(pageable)).thenReturn(projectsPage);
        when(projectMapper.projectToDTO(project)).thenReturn(projectDTO);

        Page<ProjectDTO> result = projectService.getProjects(pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(projectDTO, result.getContent().getFirst());
        verify(projectRepository).findAll(pageable);
    }

    @Test
    void getProjectById_NotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());
        Optional<Project> result = projectService.getProjectById(1L);
        assertFalse(result.isPresent());
    }

    @Test
    void getProjectById_Success() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        Optional<Project> result = projectService.getProjectById(1L);
        assertTrue(result.isPresent());
        assertEquals(project, result.get());
    }

    @Test
    void saveNewProject_Success() {
        project.setVersion(null);
        when(projectRepository.save(any(Project.class))).thenReturn(project);
        when(projectMapper.projectToDTO(project)).thenReturn(projectDTO);
        ProjectDTO result = projectService.saveNewProject(project);
        assertEquals(projectDTO, result);
        assertEquals(1L, project.getVersion());
        verify(projectRepository).save(project);
    }

    @Test
    void updateProject_Success() {
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectRepository.save(any(Project.class))).thenReturn(project);
        when(projectMapper.projectToDTO(project)).thenReturn(projectDTO);
        ProjectDTO result = projectService.updateProject(1L, project);
        assertEquals(projectDTO, result);
        verify(projectRepository).save(project);
    }

    @Test
    void updateProject_NotFound() {
        when(projectRepository.existsById(1L)).thenReturn(false);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> projectService.updateProject(1L, project));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void updateProject_ThrowsOptimisticLockException() {
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectRepository.save(project)).thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> projectService.updateProject(1L, project));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Project was updated concurrently", exception.getReason());
        verify(projectRepository).save(project);
    }

    @Test
    void updateProject_VersionNotInRequest_ThrowsBadRequest() {
        Long projectId = 1L;
        Project newProject = new Project();
        newProject.setId(projectId);
        newProject.setVersion(null);

        when(projectRepository.existsById(projectId)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            projectService.updateProject(projectId, newProject);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(projectRepository).existsById(projectId);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void savePatchedProject_NonExistingId_ThrowsNotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> projectService.savePatchedProject(1L, patchedProject));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Project not found", exception.getReason());
    }

    @Test
    void savePatchedProject_NonExistingTask_ThrowsNotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(taskRepository.findById(1L)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> projectService.savePatchedProject(1L, patchedProject));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Task not found", exception.getReason());
    }

    @Test
    void savePatchedProject_NonExistingUser_ThrowsNotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userInfoRepository.findById(2L)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> projectService.savePatchedProject(1L, patchedProject));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("User not found", exception.getReason());
    }

    @Test
    void savePatchedProject_ThrowsOptimisticLockException() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userInfoRepository.findById(2L)).thenReturn(Optional.of(userInfo));
        when(projectRepository.save(project)).thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> projectService.savePatchedProject(1L, patchedProject));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Project was updated concurrently", exception.getReason());
        verify(projectRepository).save(project);
    }

    @Test
    void savePatchedProject_NoVersionInRequest_ThrowsBadRequest() {
        patchedProject.setVersion(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            projectService.savePatchedProject(1L, patchedProject);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(projectRepository, never()).existsById(1L);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void savePatchedProject_Success() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userInfoRepository.findById(2L)).thenReturn(Optional.of(userInfo));
        doNothing().when(beanUtilsWrapper).copyProperties(patchedProject, project,
                "id", "version", "tasks", "participants");
        when(projectRepository.save(project)).thenReturn(project);
        when(projectMapper.projectToDTO(project)).thenReturn(projectDTO);
        ProjectDTO result = projectService.savePatchedProject(1L, patchedProject);
        assertEquals(projectDTO, result);
        assertTrue(project.getTasks().contains(task));
        assertTrue(project.getParticipants().contains(userInfo));
        verify(beanUtilsWrapper).copyProperties(patchedProject, project,
                "id", "version", "tasks", "participants");
        verify(projectRepository).save(project);
        verify(projectMapper).projectToDTO(project);
    }

    @Test
    void deleteProjectById_Success() {
        when(projectRepository.existsById(1L)).thenReturn(true);
        projectService.deleteProjectById(1L);
        verify(projectRepository).deleteById(1L);
    }

    @Test
    void deleteProjectById_NotFound() {
        when(projectRepository.existsById(1L)).thenReturn(false);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> projectService.deleteProjectById(1L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }



}
