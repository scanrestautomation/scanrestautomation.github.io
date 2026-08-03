package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
	private static final Logger logger = LoggerFactory.getLogger(UserController.class);
	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
		logger.info("UserController initialized");
	}

	@GetMapping
	public ResponseEntity<List<User>> getAllUsers() {
		logger.info("GET /api/users - Fetching all users");
		List<User> users = userService.getAllUsers();
		logger.info("GET /api/users - Returned {} users", users.size());
		return ResponseEntity.ok(users);
	}

	@GetMapping("/{id}")
	public ResponseEntity<User> getUserById(@PathVariable Long id) {
		logger.info("GET /api/users/{} - Fetching user by id", id);
		return userService.getUserById(id)
				.map(user -> {
					logger.info("GET /api/users/{} - User found: {}", id, user);
					return ResponseEntity.ok(user);
				})
				.orElseGet(() -> {
					logger.warn("GET /api/users/{} - User not found", id);
					return ResponseEntity.notFound().build();
				});
	}

	@PostMapping
	public ResponseEntity<User> createUser(@RequestBody User user) {
		logger.info("POST /api/users - Creating new user: {}", user);
		User createdUser = userService.createUser(user);
		logger.info("POST /api/users - User created successfully with id: {}", createdUser.getId());
		return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
	}

	@PutMapping("/{id}")
	public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
		logger.info("PUT /api/users/{} - Updating user with data: {}", id, user);
		return userService.updateUser(id, user)
				.map(updatedUser -> {
					logger.info("PUT /api/users/{} - User updated successfully: {}", id, updatedUser);
					return ResponseEntity.ok(updatedUser);
				})
				.orElseGet(() -> {
					logger.warn("PUT /api/users/{} - User not found for update", id);
					return ResponseEntity.notFound().build();
				});
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
		logger.info("DELETE /api/users/{} - Deleting user", id);
		boolean deleted = userService.deleteUser(id);
		if (deleted) {
			logger.info("DELETE /api/users/{} - User deleted successfully", id);
			return ResponseEntity.noContent().build();
		} else {
			logger.warn("DELETE /api/users/{} - User not found for deletion", id);
			return ResponseEntity.notFound().build();
		}
	}
}
