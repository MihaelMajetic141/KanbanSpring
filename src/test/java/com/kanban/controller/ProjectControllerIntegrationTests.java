package com.kanban.controller;

import com.kanban.model.dto.ProjectDTO;
import com.kanban.model.payload.JwtResponse;
import com.kanban.model.payload.LoginRequest;
import com.kanban.model.payload.RegistrationRequest;
import com.kanban.repository.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class ProjectControllerIntegrationTests {

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
        return "http://localhost:" + port + "/api/projects";
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
    void createAndGetAllProjects() {
        HttpHeaders authHeaders = getAuthHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        // Create a new project
        String projectJson = "{\"name\": \"Test Project\"}";
        HttpEntity<String> createRequest = new HttpEntity<>(projectJson, authHeaders);
        ResponseEntity<ProjectDTO> createResponse = restTemplate.postForEntity(
                baseUrl() + "/new", createRequest, ProjectDTO.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        Long createdId = createResponse.getBody().getId();
        assertThat(createdId).isNotNull();

        // Get all projects
        HttpEntity<Void> getAllRequest = new HttpEntity<>(authHeaders);
        ResponseEntity<List> getAllResponse = restTemplate.exchange(
                baseUrl() + "/getAll", HttpMethod.GET, getAllRequest, List.class
        );
        assertThat(getAllResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAllResponse.getBody()).isNotNull();
        assertThat(getAllResponse.getBody().size()).isGreaterThanOrEqualTo(1);

        // Verify the created project is in the list
        boolean found = false;
        for (Object obj : getAllResponse.getBody()) {
            LinkedHashMap<String, Object> projectMap = (LinkedHashMap<String, Object>) obj;
            if (projectMap.get("id").equals(Math.toIntExact(createdId))) {
                found = true;
                assertThat(projectMap.get("name")).isEqualTo("Test Project");
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void createAndGetProject() {
        HttpHeaders authHeaders = getAuthHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        // Create a new project
        String projectJson = "{\"name\": \"Test Project\"}";
        HttpEntity<String> createRequest = new HttpEntity<>(projectJson, authHeaders);
        ResponseEntity<ProjectDTO> createResponse = restTemplate.postForEntity(
                baseUrl() + "/new", createRequest, ProjectDTO.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        Long createdId = createResponse.getBody().getId();
        assertThat(createdId).isNotNull();

        // Get the project by ID
        HttpEntity<Void> getRequest = new HttpEntity<>(authHeaders);
        ResponseEntity<ProjectDTO> getResponse = restTemplate.exchange(
                baseUrl() + "/get/" + createdId, HttpMethod.GET, getRequest, ProjectDTO.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getId()).isEqualTo(createdId);
        assertThat(getResponse.getBody().getName()).isEqualTo("Test Project");
    }

    @Test
    void updateProject() {
        HttpHeaders authHeaders = getAuthHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        // Create a new project
        String projectJson = "{\"name\": \"Original Project\"}";
        HttpEntity<String> createRequest = new HttpEntity<>(projectJson, authHeaders);
        ResponseEntity<ProjectDTO> createResponse = restTemplate.postForEntity(
                baseUrl() + "/new", createRequest, ProjectDTO.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        Long createdId = createResponse.getBody().getId();
        assertThat(createdId).isNotNull();

        // Update the project
        String updatedJson = "{\"id\": " + createdId + ", \"version\": " + 1L + ", \"name\": \"Updated Project\"}";
        HttpEntity<String> updateRequest = new HttpEntity<>(updatedJson, authHeaders);
        ResponseEntity<ProjectDTO> updateResponse = restTemplate.exchange(
                baseUrl() + "/update/" + createdId, HttpMethod.PUT, updateRequest, ProjectDTO.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).isNotNull();
        assertThat(updateResponse.getBody().getName()).isEqualTo("Updated Project");

        // Verify the update by getting the project
        HttpEntity<Void> getRequest = new HttpEntity<>(authHeaders);
        ResponseEntity<ProjectDTO> getResponse = restTemplate.exchange(
                baseUrl() + "/get/" + createdId, HttpMethod.GET, getRequest, ProjectDTO.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getName()).isEqualTo("Updated Project");
    }

    @Test
    void patchProject() {
        HttpHeaders authHeaders = getAuthHeaders();
        authHeaders.setContentType(MediaType.valueOf("application/merge-patch+json"));

        // Create a new project
        String projectJson = "{\"name\": \"Original Project\"}";
        HttpEntity<String> createRequest = new HttpEntity<>(projectJson, authHeaders);
        // createRequest.getHeaders().setContentType(MediaType.APPLICATION_JSON); // Temporarily set for create
        ResponseEntity<ProjectDTO> createResponse = restTemplate.postForEntity(
                baseUrl() + "/new", createRequest, ProjectDTO.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        Long createdId = createResponse.getBody().getId();
        assertThat(createdId).isNotNull();

        // Patch the project
        String patchJson = "{\"name\": \"Patched Project\"}";
        HttpEntity<String> patchRequest = new HttpEntity<>(patchJson, authHeaders);
        ResponseEntity<ProjectDTO> patchResponse = restTemplate.exchange(
                baseUrl() + "/patch/" + createdId, HttpMethod.PATCH, patchRequest, ProjectDTO.class
        );
        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResponse.getBody()).isNotNull();
        assertThat(patchResponse.getBody().getName()).isEqualTo("Patched Project");

        // Verify the patch by getting the project
        HttpEntity<Void> getRequest = new HttpEntity<>(authHeaders);
        ResponseEntity<ProjectDTO> getResponse = restTemplate.exchange(
                baseUrl() + "/get/" + createdId, HttpMethod.GET, getRequest, ProjectDTO.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getName()).isEqualTo("Patched Project");
    }

    @Test
    void deleteProject() {
        HttpHeaders authHeaders = getAuthHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        // Create a new project
        String projectJson = "{\"name\": \"Project to Delete\"}";
        HttpEntity<String> createRequest = new HttpEntity<>(projectJson, authHeaders);
        ResponseEntity<ProjectDTO> createResponse = restTemplate.postForEntity(
                baseUrl() + "/new", createRequest, ProjectDTO.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        Long createdId = createResponse.getBody().getId();
        assertThat(createdId).isNotNull();

        // Delete the project
        HttpEntity<Void> deleteRequest = new HttpEntity<>(authHeaders);
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl() + "/delete/" + createdId, HttpMethod.DELETE, deleteRequest, Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify the project is deleted
//        HttpEntity<Void> getRequest = new HttpEntity<>(authHeaders);
//        ResponseEntity<ProjectDTO> getResponse = restTemplate.exchange(
//                baseUrl() + "/get/" + createdId, HttpMethod.GET, getRequest, ProjectDTO.class
//        );
//        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }


}
