package com.kanban.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kanban.model.enums.TaskPriority;
import com.kanban.model.enums.TaskStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tasks")
public class Task {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Version
        private Long version;

        @Column(nullable=false)
        @NotBlank
        @Size(max = 255)
        private String title;

        @Size(max = 2000)
        private String description;

        @NotNull
        @Enumerated(EnumType.STRING)
        private TaskStatus status;

        @NotNull
        @Enumerated(EnumType.STRING)
        private TaskPriority priority;

        @CreationTimestamp
        private LocalDateTime createdAt;

        @UpdateTimestamp
        private LocalDateTime updatedAt;

        private LocalDateTime dueAt;

        @ManyToMany
        @JoinTable(
                name = "task_assignees",
                joinColumns = @JoinColumn(name = "task_id"),
                inverseJoinColumns = @JoinColumn(name = "user_id"))
        @JsonIgnoreProperties({"userProjects", "password"})
        private Set<UserInfo> taskAssignees = new HashSet<>();

}

