package com.kanban.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban.model.Project;
import com.kanban.model.dto.ProjectDTO;
import com.kanban.service.ProjectService;
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


@RestController
@RequestMapping("/api/projects")
@AllArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final PatchUtils patchUtils;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/getAll")
    public ResponseEntity<?> getAllProjects(
            @PageableDefault(
                    size = 10,
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        Page<ProjectDTO> page = projectService.getProjects(pageable);
        return ResponseEntity.ok(page.getContent());
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getProjectById(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    @PostMapping("/new")
    public ResponseEntity<?> createProject(@RequestBody Project project) {
        ProjectDTO newProject = projectService.saveNewProject(project);
        messagingTemplate.convertAndSend("/topic/projects", newProject);
        return ResponseEntity.ok(newProject);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody Project project
    ) {
        try {
            ProjectDTO updated = projectService.updateProject(id, project);
            messagingTemplate.convertAndSend("/topic/projects", updated);
            return ResponseEntity.ok(updated);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task was updated by another client");
        }
    }

    @PatchMapping(
            path = "/patch/{id}",
            consumes = "application/merge-patch+json")
    public ResponseEntity<?> updateProject(
            @PathVariable Long id,
            @RequestBody JsonNode patchNode
    ) {
        try {
            Project existingProject = projectService.getProjectById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

            JsonNode existingNode = objectMapper.valueToTree(existingProject);
            JsonNode merged = patchUtils.merge(existingNode, patchNode);

            Project patchedProject = objectMapper.treeToValue(merged, Project.class);
            patchedProject.setId(existingProject.getId());
            validator.validate(patchedProject);

            ProjectDTO savedProject = projectService.savePatchedProject(id, patchedProject);
            messagingTemplate.convertAndSend("/topic/projects", savedProject);
            return ResponseEntity.ok(savedProject);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteProjectById(@PathVariable Long id) {
        projectService.deleteProjectById(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}
