package com.kanban.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban.model.Task;
import com.kanban.model.dto.TaskDTO;
import com.kanban.service.TaskService;
import com.kanban.util.PatchUtils;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;


@RestController
@RequestMapping("/api/tasks")
@AllArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final PatchUtils patchUtils;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/getAll")
    public ResponseEntity<?> getTasks(
            @RequestParam(required = false) String status,
            @PageableDefault(
                size = 10,
                sort = "createdAt",
                direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        Page<TaskDTO> page = taskService.getTasks(status, pageable);
        return ResponseEntity.ok(page.getContent());
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getTaskById(@PathVariable Long id) {
        Optional<Task> taskOptional = taskService.getTaskById(id);
        if (taskOptional.isPresent()) {
            return ResponseEntity.ok(taskOptional.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/new")
    public ResponseEntity<?> createTask(@RequestBody Task task) {
        TaskDTO newTask = taskService.saveNewTask(task);
        messagingTemplate.convertAndSend("/topic/tasks", newTask);
        return ResponseEntity.status(HttpStatus.CREATED).body(newTask);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody Task task
    ) {
        try {
            TaskDTO updatedTask = taskService.updateTask(id, task);
            messagingTemplate.convertAndSend("/topic/tasks", updatedTask);
            return ResponseEntity.ok(updatedTask);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task was updated by another client");
        }
    }

    @PatchMapping(
            path = "/patch/{id}",
            consumes = "application/merge-patch+json")
    public ResponseEntity<?> patchTask(
            @PathVariable Long id,
            @RequestBody JsonNode patchNode
    ) {
        try {
            Task existingTask = taskService.getTaskById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

            JsonNode existingNode = objectMapper.valueToTree(existingTask);
            JsonNode merged = patchUtils.merge(existingNode, patchNode);
            Task patchedTask = objectMapper.treeToValue(merged, Task.class);
            patchedTask.setId(existingTask.getId());
            validator.validate(patchedTask);

            TaskDTO savedTask = taskService.savePatchedTask(id, patchedTask);
            messagingTemplate.convertAndSend("/topic/tasks", savedTask);
            return ResponseEntity.ok(savedTask);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable Long id) {
        taskService.deleteTaskById(id);
        messagingTemplate.convertAndSend("/topic/tasks", id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
