package com.dovidioai.web;

import com.dovidioai.service.AuthService;
import com.dovidioai.service.CurrentUserService;
import com.dovidioai.web.dto.AuthResponse;
import com.dovidioai.web.dto.LoginRequest;
import com.dovidioai.web.dto.RegisterRequest;
import com.dovidioai.web.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.me(currentUserService.requireUserId());
    }
}
