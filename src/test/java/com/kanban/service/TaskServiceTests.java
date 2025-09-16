package com.kanban.service;


import com.kanban.mapper.TaskMapper;
import com.kanban.model.Project;
import com.kanban.model.Task;
import com.kanban.model.UserInfo;
import com.kanban.model.dto.TaskDTO;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTests {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private BeanUtilsWrapper beanUtilsWrapper;

    @InjectMocks
    private TaskService taskService;

    private Task task;
    private TaskDTO taskDTO;
    private Task patchedTask;
    private UserInfo user;
    private Project project;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        task = Task.builder()
                .id(1L)
                .version(1L)
                .title("Test Task")
                .description("This is a test task description")
                .status(TaskStatus.TO_DO)
                .priority(TaskPriority.MEDIUM)
                .dueAt(LocalDateTime.now().plusDays(7))
                .taskAssignees(new HashSet<>())
                .build();

        taskDTO = TaskDTO.builder()
                .id(1L)
                .title("Test Task")
                .description("This is a test task description")
                .status(TaskStatus.TO_DO.name())
                .priority(TaskPriority.MEDIUM.name())
                .dueAt(LocalDateTime.now().plusDays(7))
                .assigneeIds(new ArrayList<>())
                .build();

        patchedTask = Task.builder()
                .title("Updated Task")
                .version(1L)
                .description("Updated description")
                .status(TaskStatus.IN_PROGRESS)
                .priority(TaskPriority.HIGH)
                .dueAt(LocalDateTime.now().plusDays(5))
                .taskAssignees(new HashSet<>())
                .build();

        user = UserInfo.builder()
                .id(2L)
                .username("test_user")
                .build();
        patchedTask.getTaskAssignees().add(user);

        project = new Project();
        project.setId(1L);
        Set<Task> tasks = new HashSet<>();
        tasks.add(task);
        project.setTasks(tasks);

        pageable = PageRequest.of(0, 10);
    }

    @Test
    void getTasks_NoStatus_ReturnsAllTasks() {
        Page<Task> taskPage = new PageImpl<>(List.of(task));
        when(taskRepository.findAll(pageable)).thenReturn(taskPage);
        when(taskMapper.taskToDTO(task)).thenReturn(taskDTO);

        Page<TaskDTO> result = taskService.getTasks(null, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(taskDTO, result.getContent().getFirst());
        verify(taskRepository).findAll(pageable);
    }

    @Test
    void getTasks_WithValidStatus_ReturnsFilteredTasks() {
        Page<Task> taskPage = new PageImpl<>(List.of(task));
        when(taskRepository.findByStatus(TaskStatus.TO_DO, pageable)).thenReturn(taskPage);
        when(taskMapper.taskToDTO(task)).thenReturn(taskDTO);

        Page<TaskDTO> result = taskService.getTasks(TaskStatus.TO_DO.name(), pageable);

        assertEquals(1, result.getContent().size());
        verify(taskRepository).findByStatus(TaskStatus.TO_DO, pageable);
    }

    @Test
    void getTasks_InvalidStatus_ThrowsBadRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.getTasks("INVALID", pageable));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getTaskById_ExistingId_ReturnsTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        Optional<Task> result = taskService.getTaskById(1L);

        assertTrue(result.isPresent());
        assertEquals(task, result.get());
    }

    @Test
    void getTaskById_NonExistingId_ReturnsEmpty() {
        when(taskRepository.findById(1L)).thenReturn(Optional.empty());

        Optional<Task> result = taskService.getTaskById(1L);

        assertFalse(result.isPresent());
    }

    @Test
    void saveNewTask_SavesAndReturnsDTO() {
        task.setVersion(null);
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        when(taskMapper.taskToDTO(task)).thenReturn(taskDTO);

        TaskDTO result = taskService.saveNewTask(task);

        assertEquals(taskDTO, result);
        assertNotNull(task.getCreatedAt());
        assertEquals(1L, task.getVersion());
        verify(taskRepository).save(task);
    }

    @Test
    void updateTask_Success() {
        when(taskRepository.existsById(1L)).thenReturn(true);
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        when(taskMapper.taskToDTO(task)).thenReturn(taskDTO);

        TaskDTO result = taskService.updateTask(1L, task);

        assertEquals(taskDTO, result);
        assertNotNull(task.getUpdatedAt());
        verify(taskRepository).save(task);
    }

    @Test
    void updateTask_NonExistingId_ThrowsNotFound() {
        when(taskRepository.existsById(1L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.updateTask(1L, task));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void updateTask_ThrowsOptimisticLockException() {
        when(taskRepository.existsById(1L)).thenReturn(true);
        when(taskRepository.save(task)).thenThrow(new ObjectOptimisticLockingFailureException(Task.class, 1L));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.updateTask(1L, task));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Task was updated concurrently", exception.getReason());
        verify(taskRepository).save(task);
    }

    @Test
    void updateTask_VersionNotInRequest_ThrowsBadRequest() {
        patchedTask.setVersion(null);

        when(taskRepository.existsById(1L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            taskService.updateTask(1L, patchedTask);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(taskRepository).existsById(1L);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void savePatchedTask_ValidIdAndData_SavesAndReturnsDTO() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userInfoRepository.findById(2L)).thenReturn(Optional.of(user));
        doNothing().when(beanUtilsWrapper).copyProperties(patchedTask, task,
                "id", "version", "taskAssignees", "createdAt", "updatedAt");
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.taskToDTO(task)).thenReturn(taskDTO);

        TaskDTO result = taskService.savePatchedTask(1L, patchedTask);

        assertNotNull(result);
        assertEquals(taskDTO, result);
        assertNotNull(task.getUpdatedAt());
        assertTrue(task.getTaskAssignees().contains(user));
        verify(beanUtilsWrapper).copyProperties(patchedTask, task,
                "id", "version", "taskAssignees", "createdAt", "updatedAt");
        verify(taskRepository).save(task);
        verify(taskMapper).taskToDTO(task);
    }

    @Test
    void savePatchedTask_NonExistingId_ThrowsNotFound() {
        when(taskRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.savePatchedTask(1L, patchedTask));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Task not found", exception.getReason());

        verify(taskRepository).findById(1L);
        verifyNoInteractions(beanUtilsWrapper, taskMapper, userInfoRepository);
    }

    @Test
    void savePatchedTask_NonExistingAssignee_ThrowsNotFound() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userInfoRepository.findById(2L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.savePatchedTask(1L, patchedTask));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("User not found", exception.getReason());

        verify(taskRepository).findById(1L);
        verifyNoMoreInteractions(taskRepository);
        verify(beanUtilsWrapper).copyProperties(patchedTask, task,
                "id", "version", "taskAssignees", "createdAt", "updatedAt");
        verify(userInfoRepository).findById(2L);
        verifyNoInteractions(taskMapper);
    }

    @Test
    void savePatchedTask_ConcurrentUpdate_ThrowsConflict() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userInfoRepository.findById(2L)).thenReturn(Optional.of(user));
        doNothing().when(beanUtilsWrapper).copyProperties(patchedTask, task,
                "id", "version", "taskAssignees", "createdAt", "updatedAt");
        when(taskRepository.save(task)).thenThrow(new ObjectOptimisticLockingFailureException(Task.class, 1L));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.savePatchedTask(1L, patchedTask));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Task was updated concurrently", exception.getReason());
        verify(taskRepository).save(task);
    }

    @Test
    void savePatchedTask_NullAssignees_SkipsAssigneeUpdate() {
        patchedTask.setTaskAssignees(null);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        doNothing().when(beanUtilsWrapper).copyProperties(patchedTask, task,
                "id", "version", "taskAssignees", "createdAt", "updatedAt");
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.taskToDTO(task)).thenReturn(taskDTO);

        TaskDTO result = taskService.savePatchedTask(1L, patchedTask);

        assertNotNull(result);
        assertEquals(taskDTO, result);
        assertTrue(task.getTaskAssignees().isEmpty()); // Assignees not cleared or updated
        verify(beanUtilsWrapper).copyProperties(patchedTask, task,
                "id", "version", "taskAssignees", "createdAt", "updatedAt");
        verify(taskRepository).save(task);
        verifyNoInteractions(userInfoRepository);
    }

    @Test
    void deleteTaskById_NonExistingId_ThrowsNotFound() {
        when(taskRepository.findById(1L)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> taskService.deleteTaskById(1L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(taskRepository).findById(1L);
        verifyNoInteractions(projectRepository, taskMapper, userInfoRepository, beanUtilsWrapper);
    }

    @Test
    void savePatchedTask_NoVersionInRequest_ThrowsBadRequest() {
        patchedTask.setVersion(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            taskService.savePatchedTask(1L, patchedTask);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(taskRepository, never()).existsById(1L);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void deleteTaskById_ProjectNotFound_SkipsDeleteProjectTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(projectRepository.findAll()).thenReturn(List.of());

        taskService.deleteTaskById(1L);

        verify(taskRepository).findById(1L);
        verify(projectRepository).findAll();
        verifyNoMoreInteractions(projectRepository);
        verify(taskRepository).deleteById(1L);

    }

    @Test
    void deleteTaskById_ProjectFound_DeletesProjectTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        taskService.deleteTaskById(1L);

        assertFalse(project.getTasks().contains(task));
        verify(taskRepository).findById(1L);
        verify(projectRepository).findAll();
        verify(projectRepository).save(project);
        verify(taskRepository).deleteById(1L);
    }

}
