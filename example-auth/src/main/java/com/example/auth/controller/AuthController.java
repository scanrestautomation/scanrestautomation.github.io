package com.example.auth.controller;

import com.example.auth.model.LoginRequest;
import com.example.auth.model.LoginResponse;
import com.example.auth.model.UserProfile;
import com.example.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller demonstrating token-based auth flow.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/**
	 * POST /api/auth/login
	 * Authenticate with username/password, returns a token.
	 */
	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
		String token = authService.login(request.getUsername(), request.getPassword());
		if (token == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		LoginResponse response = new LoginResponse(token, request.getUsername());
		return ResponseEntity.ok(response);
	}

	/**
	 * GET /api/auth/profile
	 * Protected endpoint — requires X-Auth-Token header.
	 */
	@GetMapping("/profile")
	public ResponseEntity<UserProfile> getProfile(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
		if (token == null || token.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		UserProfile profile = authService.validateToken(token);
		if (profile == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		return ResponseEntity.ok(profile);
	}

	/**
	 * POST /api/auth/logout
	 * Invalidate the token.
	 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
		if (token != null && !token.isBlank()) {
			authService.logout(token);
		}
		return ResponseEntity.noContent().build();
	}
}
