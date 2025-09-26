package com.kanban.mapper;

import com.kanban.model.Task;
import com.kanban.model.UserInfo;
import com.kanban.model.dto.TaskDTO;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;


@Component
public class TaskMapper {

    public TaskDTO taskToDTO(Task task) {
        if (task == null) {
            return null;
        }

        return TaskDTO.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus() != null ? task.getStatus().name() : null)
                .priority(task.getPriority() != null ? task.getPriority().name() : null)
                .dueAt(task.getDueAt())
                .assigneeIds(task.getTaskAssignees().stream().map(UserInfo::getId).toList())
                .build();
    }

}