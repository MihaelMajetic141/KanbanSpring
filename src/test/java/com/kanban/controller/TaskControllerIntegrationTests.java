package com.kanban.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kanban.model.Task;
import com.kanban.model.dto.ProjectDTO;
import com.kanban.model.dto.TaskDTO;
import com.kanban.model.enums.TaskPriority;
import com.kanban.model.enums.TaskStatus;
import com.kanban.model.payload.JwtResponse;
import com.kanban.model.payload.LoginRequest;
import com.kanban.model.payload.RegistrationRequest;
import com.kanban.repository.UserInfoRepository;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class TaskControllerIntegrationTests {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("integration-tests-db")
            .withUsername("sa")
            .withPassword("sa");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    UserInfoRepository userInfoRepository;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    private String token;

    private static boolean isUserRegistered = false;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/tasks";
    }

    private String authUrl() {
        return "http://localhost:" + port + "/api/auth";
    }

    @BeforeEach
    void registerAndLogin() {
        if (!isUserRegistered) {
            userInfoRepository.deleteAll();
            RegistrationRequest registrationRequest = new RegistrationRequest(
                    "testuser", "testuser@gmail.com", "testpassword");
            HttpEntity<RegistrationRequest> registerRequest = new HttpEntity<>(registrationRequest, new HttpHeaders());

            ResponseEntity<String> registerResponse = restTemplate.postForEntity(
                    authUrl() + "/register", registerRequest, String.class
            );
            assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(registerResponse.getBody()).isEqualTo("User registered successfully!");

            isUserRegistered = true;
        }

        LoginRequest loginRequest = new LoginRequest("testuser", "testpassword");
        HttpEntity<LoginRequest> loginRequestEntity = new HttpEntity<>(loginRequest, new HttpHeaders());

        ResponseEntity<JwtResponse> loginResponse = restTemplate.postForEntity(
                authUrl() + "/login", loginRequestEntity, JwtResponse.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        this.token = loginResponse.getBody().getAccessToken();
        assertThat(this.token).isNotEmpty();
    }

    private HttpHeaders getAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    @Test
    void createAndGetAllTasks() {
        HttpHeaders authHeaders = getAuthHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        Task task = Task.builder()
                .title("Integration Create Test")
                .description("desc")
                .status(TaskStatus.TO_DO)
                .priority(TaskPriority.MEDIUM)
                .dueAt(LocalDateTime.now().plusDays(3))
                .build();

        HttpEntity<Task> createEntity = new HttpEntity<>(task, authHeaders);

        ResponseEntity<TaskDTO> createResponse = restTemplate.postForEntity(
                baseUrl() + "/new", createEntity, TaskDTO.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TaskDTO created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo(task.getTitle());
        Long createdId = createResponse.getBody().getId();
        assertThat(createdId).isNotNull();

        HttpEntity<Void> getAllRequest = new HttpEntity<>(authHeaders);
        ResponseEntity<List> getAllResponse = restTemplate.exchange(
                baseUrl() + "/getAll", HttpMethod.GET, getAllRequest, List.class
        );
        assertThat(getAllResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAllResponse.getBody()).isNotNull();
        assertThat(getAllResponse.getBody().size()).isGreaterThanOrEqualTo(1);

        boolean found = false;
        for (Object obj : getAllResponse.getBody()) {
            LinkedHashMap<String, Object> projectMap = (LinkedHashMap<String, Object>) obj;
            if (projectMap.get("id").equals(Math.toIntExact(createdId))) {
                found = true;
                assertThat(projectMap.get("title")).isEqualTo("Integration Create Test");
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void createAndGetTask() {
        HttpHeaders authHeaders = getAuthHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        Task task = Task.builder()
                .title("Integration Create Test")
                .description("desc")
                .status(TaskStatus.TO_DO)
                .priority(TaskPriority.MEDIUM)
                .dueAt(LocalDateTime.now().plusDays(3))
                .build();

        HttpEntity<Task> createEntity = new HttpEntity<>(task, authHeaders);

        ResponseEntity<TaskDTO> createResp = restTemplate.postForEntity(
                baseUrl() + "/new", createEntity, TaskDTO.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TaskDTO created = createResp.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo(task.getTitle());

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders);

        ResponseEntity<JsonNode> getResp = restTemplate.exchange(
                baseUrl() + "/get/" + created.getId(), HttpMethod.GET, getEntity, JsonNode.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = getResp.getBody();
        Assertions.assertNotNull(body);
        assertThat(body.get("title").asText()).isEqualTo("Integration Create Test");
    }

    @Test
    void updateProject() {
        HttpHeaders authHeaders = getAuthHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        Task task = Task.builder()
                .title("Integration Create Test")
                .description("desc")
                .status(TaskStatus.TO_DO)
                .priority(TaskPriority.MEDIUM)
                .dueAt(LocalDateTime.now().plusDays(3))
                .build();

        HttpEntity<Task> createEntity = new HttpEntity<>(task, authHeaders);

        ResponseEntity<TaskDTO> createResponse = restTemplate.postForEntity(
                baseUrl() + "/new", createEntity, TaskDTO.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TaskDTO created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo(task.getTitle());
        Long createdId = createResponse.getBody().getId();
        assertThat(createdId).isNotNull();

        Task updatedTask = Task.builder()
                .id(createdId)
                .title("Updated Task")
                .version(1L)
                .description("desc")
                .status(TaskStatus.IN_PROGRESS)
                .priority(TaskPriority.HIGH)
                .build();
        HttpEntity<Task> updateRequest = new HttpEntity<>(updatedTask, authHeaders);
        ResponseEntity<TaskDTO> updateResponse = restTemplate.exchange(
                baseUrl() + "/update/" + createdId, HttpMethod.PUT, updateRequest, TaskDTO.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).isNotNull();
        assertThat(updateResponse.getBody().getTitle()).isEqualTo("Updated Task");

        HttpEntity<Void> getRequest = new HttpEntity<>(authHeaders);
        ResponseEntity<TaskDTO> getResponse = restTemplate.exchange(
                baseUrl() + "/get/" + createdId, HttpMethod.GET, getRequest, TaskDTO.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertNotNull(getResponse.getBody());
        assertThat(getResponse.getBody().getTitle()).isEqualTo("Updated Task");
    }

    @Test
    void patchTask_mergePatch_updatesField() {
        HttpHeaders authHeaders = getAuthHeaders();

        Task task = Task.builder()
                .title("Patch test")
                .description("desc")
                .status(TaskStatus.TO_DO)
                .priority(TaskPriority.LOW)
                .build();

        HttpEntity<Task> createEntity = new HttpEntity<>(task, authHeaders);

        ResponseEntity<TaskDTO> createResponse = restTemplate.postForEntity(
                baseUrl() + "/new",
                createEntity,
                TaskDTO.class
        );
        TaskDTO created = createResponse.getBody();
        assertThat(created).isNotNull();

        String patchJson = "{\"title\":\"Patched title\"}";
        HttpHeaders patchHeaders = new HttpHeaders(authHeaders);
        patchHeaders.setContentType(MediaType.valueOf("application/merge-patch+json"));

        HttpEntity<String> patchReq = new HttpEntity<>(patchJson, patchHeaders);

        ResponseEntity<TaskDTO> patchResp = restTemplate.exchange(
                baseUrl() + "/patch/" + created.getId(),
                HttpMethod.PATCH, patchReq,
                TaskDTO.class
        );

        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskDTO patched = patchResp.getBody();
        assertThat(patched).isNotNull();
        assertThat(patched.getTitle()).isEqualTo("Patched title");
    }

    @Test
    void deleteTask_returnsNoContent() {
        HttpHeaders authHeaders = getAuthHeaders();

        Task task = Task.builder()
                .title("Delete me")
                .description("d")
                .status(TaskStatus.TO_DO)
                .priority(TaskPriority.LOW)
                .build();

        HttpEntity<Task> createEntity = new HttpEntity<>(task, authHeaders);

        ResponseEntity<TaskDTO> createResp = restTemplate.postForEntity(
                baseUrl() + "/new",
                createEntity,
                TaskDTO.class
        );
        TaskDTO created = createResp.getBody();
        assertThat(created).isNotNull();

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders);

        ResponseEntity<Void> deleteResp = restTemplate.exchange(baseUrl() + "/delete/" + created.getId(),
                HttpMethod.DELETE, deleteEntity, Void.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders);

        ResponseEntity<JsonNode> getResp = restTemplate.exchange(baseUrl() + "/get/" + created.getId(),
                HttpMethod.GET, getEntity, JsonNode.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void whenCreate_thenWebSocketSubscriberReceivesTaskDTO() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws";

        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        httpHeaders.add("Authorization", "Bearer " + token);

        CompletableFuture<JsonNode> future = new CompletableFuture<>();

        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {};
        CompletableFuture<StompSession> sessionFuture = stompClient.connectAsync(wsUrl, httpHeaders, handler);
        StompSession session = sessionFuture.get(2, TimeUnit.SECONDS);

        session.subscribe("/topic/tasks", new StompFrameHandler() {
            @Override
            public @NotNull Type getPayloadType(@NotNull StompHeaders headers) {
                return JsonNode.class;
            }
            @Override
            public void handleFrame(@NotNull StompHeaders headers, Object payload) {
                future.complete((JsonNode) payload);
            }
        });

        Thread.sleep(200);

        HttpHeaders authHeaders = getAuthHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        Task create = Task.builder()
                .title("WS broadcast")
                .description("ws")
                .status(TaskStatus.TO_DO)
                .priority(TaskPriority.MEDIUM)
                .build();

        HttpEntity<Task> createEntity = new HttpEntity<>(create, authHeaders);

        ResponseEntity<TaskDTO> createResp = restTemplate.postForEntity(
                baseUrl() + "/new",
                createEntity,
                TaskDTO.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode message = future.get(5, TimeUnit.SECONDS);
        assertThat(message.get("title").asText()).isEqualTo("WS broadcast");

        session.disconnect();
        stompClient.stop();
    }
}
