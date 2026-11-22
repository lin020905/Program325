package org.xidian.program325.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xidian.program325.common.ApiResponse;
import org.xidian.program325.common.Sessions;
import org.xidian.program325.pojo.Account.LoginRequest;
import org.xidian.program325.pojo.Account.SessionUser;
import org.xidian.program325.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ApiResponse<SessionUser> login(@RequestBody LoginRequest credentials, HttpServletRequest request) {
        SessionUser user = auth.login(credentials);
        Sessions.login(request, user);
        return ApiResponse.ok(user);
    }

    @GetMapping("/session")
    public ApiResponse<SessionUser> session(HttpServletRequest request) {
        return ApiResponse.ok(Sessions.requireUser(request));
    }

    @DeleteMapping("/session")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        Sessions.logout(request);
        return ApiResponse.ok(null);
    }
}
