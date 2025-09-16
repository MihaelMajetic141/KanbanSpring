package com.kanban.model.dto;


import lombok.Builder;
import lombok.Data;

import java.util.List;


@Data
@Builder
public class ProjectDTO {

    private Long id;
    private String name;
    List<Long> participantIds;
    List<Long> taskIds;

}
