package com.kanban.mapper;


import com.kanban.model.Project;
import com.kanban.model.Task;
import com.kanban.model.UserInfo;
import com.kanban.model.dto.ProjectDTO;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyList;

@Component
public class ProjectMapper {

    public ProjectDTO projectToDTO(Project project) {
        if (project == null)
            return null;
        return ProjectDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .participantIds(project.getParticipants() != null ?
                        project.getParticipants().stream()
                                .map(UserInfo::getId)
                                .toList() : emptyList())
                .taskIds(project.getTasks() != null ?
                        project.getTasks().stream()
                                .map(Task::getId)
                                .toList() : emptyList())
                .build();
    }

}
