package com.kanban.controller;

import com.kanban.model.RefreshToken;
import com.kanban.model.Role;
import com.kanban.model.UserInfo;
import com.kanban.model.enums.EnumRole;
import com.kanban.model.payload.JwtResponse;
import com.kanban.model.payload.LoginRequest;
import com.kanban.model.payload.RefreshTokenRequest;
import com.kanban.model.payload.RegistrationRequest;
import com.kanban.repository.RoleRepository;
import com.kanban.repository.UserInfoRepository;
import com.kanban.security.JwtService;
import com.kanban.security.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;


@RestController
@CrossOrigin(maxAge = 3600)
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    RefreshTokenService refreshTokenService;
    @Autowired
    UserInfoRepository userInfoRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    JwtService jwtService;
    @Autowired
    BCryptPasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody RegistrationRequest registrationRequest
    ) {
        if (userInfoRepository.existsByUsername(registrationRequest.getUsername()))
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        if (userInfoRepository.existsByEmail(registrationRequest.getEmail()))
            return ResponseEntity.badRequest().body("Error: Email is already in use!");

        if (roleRepository.findByName(EnumRole.ROLE_USER).isEmpty())
            roleRepository.save(Role.builder().name(EnumRole.ROLE_USER).build());
        if (roleRepository.findByName(EnumRole.ROLE_ADMIN).isEmpty())
            roleRepository.save(Role.builder().name(EnumRole.ROLE_ADMIN).build());

        Set<Role> basicRoleSet = new HashSet<>();
        basicRoleSet.add(new Role(1L, EnumRole.ROLE_USER));
        UserInfo user = UserInfo.builder()
                .email(registrationRequest.getEmail())
                .username(registrationRequest.getUsername())
                .password(passwordEncoder.encode(registrationRequest.getPassword()))
                .roles(basicRoleSet)
                .build();
        userInfoRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest loginRequest
    ) {
        Optional<UserInfo> user = userInfoRepository.findByUsername(loginRequest.getUsername());
        if (user.isEmpty()) {
            throw new UsernameNotFoundException("Invalid user request!");
        }

        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(), loginRequest.getPassword()));

        if (authentication.isAuthenticated()) {
            JwtResponse jwtResponse = JwtResponse.builder()
                    .accessToken(jwtService.generateToken(user.get().getEmail()))
                    .refreshToken(refreshTokenService.createRefreshToken(user.get().getEmail()).getToken())
                    .build();
            return ResponseEntity.ok().body(jwtResponse);
        } else {
            throw new UsernameNotFoundException("Invalid user request!");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestBody String refreshToken
    ) {
        Optional<RefreshToken> deletedRefreshToken = refreshTokenService.deleteRefreshToken(refreshToken);
        if (deletedRefreshToken.isPresent()) {
            return ResponseEntity.ok().body("You've been signed out!");
        } else
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refreshToken");
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest refreshTokenRequest) {
        Optional<RefreshToken> refreshTokenOpt = refreshTokenService.findByToken(refreshTokenRequest.getToken());

        if (refreshTokenOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
        }

        RefreshToken refreshToken = refreshTokenService.verifyExpiration(refreshTokenOpt.get());
        Optional<UserInfo> userInfoOpt = userInfoRepository.findById(refreshToken.getUserId());

        if (userInfoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        UserInfo userInfo = userInfoOpt.get();
        String accessToken = jwtService.generateToken(userInfo.getEmail());
        JwtResponse jwtResponse = JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenRequest.getToken())
                .build();

        return ResponseEntity.ok(jwtResponse);
    }

}
