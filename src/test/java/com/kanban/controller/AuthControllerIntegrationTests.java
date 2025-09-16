package com.kanban.controller;

import com.kanban.model.payload.JwtResponse;
import com.kanban.model.payload.LoginRequest;
import com.kanban.model.payload.RefreshTokenRequest;
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

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class AuthControllerIntegrationTests {

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

    private JwtResponse jwtResponse;

    private static boolean isUserRegistered = false;

    private String baseUrl() {
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
                    baseUrl() + "/register", registerRequest, String.class
            );
            assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(registerResponse.getBody()).isEqualTo("User registered successfully!");

            isUserRegistered = true;
        }

        LoginRequest loginRequest = new LoginRequest("testuser", "testpassword");
        HttpEntity<LoginRequest> loginRequestEntity = new HttpEntity<>(loginRequest, new HttpHeaders());

        ResponseEntity<JwtResponse> loginResponse = restTemplate.postForEntity(
                baseUrl() + "/login", loginRequestEntity, JwtResponse.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        this.jwtResponse = loginResponse.getBody();
    }


    @Test
    void logout_Success() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + jwtResponse.getAccessToken());

        HttpEntity<String> logoutRequest = new HttpEntity<>(jwtResponse.getRefreshToken(), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/logout", logoutRequest, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("You've been signed out!");
    }

    @Test
    void logout_InvalidToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + jwtResponse.getAccessToken());

        String refreshRequest = "invalid_refresh_token";
        HttpEntity<String> logoutRequest = new HttpEntity<>(refreshRequest, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/logout", logoutRequest, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("Invalid refreshToken");
    }

    @Test
    void refreshToken_Success() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(jwtResponse.getRefreshToken());
        HttpEntity<RefreshTokenRequest> refreshEntity = new HttpEntity<>(refreshRequest, headers);
        ResponseEntity<JwtResponse> response = restTemplate.postForEntity(
                baseUrl() + "/refreshToken", refreshEntity, JwtResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotEmpty();
        assertThat(response.getBody().getRefreshToken()).isEqualTo(jwtResponse.getRefreshToken());
    }

    @Test
    void refreshToken_Invalid() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest("invalid_token");
        HttpEntity<RefreshTokenRequest> refreshEntity = new HttpEntity<>(refreshRequest, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/refreshToken", refreshEntity, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("Invalid refresh token");
    }
}
