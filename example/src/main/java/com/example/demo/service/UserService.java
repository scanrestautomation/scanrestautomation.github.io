package com.example.demo.service;

import com.example.demo.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {
	private static final Logger logger = LoggerFactory.getLogger(UserService.class);
	private final List<User> users = new ArrayList<>();
	private final AtomicLong idCounter = new AtomicLong(1);

	public UserService() {
		logger.info("UserService initialized - In-memory storage ready");
	}

	public List<User> getAllUsers() {
		logger.debug("Retrieving all users from in-memory storage");
		logger.debug("Current user count: {}", users.size());
		return new ArrayList<>(users);
	}

	public Optional<User> getUserById(Long id) {
		logger.debug("Searching for user with id: {}", id);
		Optional<User> user = users.stream()
				.filter(u -> u.getId().equals(id))
				.findFirst();
		if (user.isPresent()) {
			logger.debug("User found with id: {}", id);
		} else {
			logger.debug("No user found with id: {}", id);
		}
		return user;
	}

	public User createUser(User user) {
		Long newId = idCounter.getAndIncrement();
		user.setId(newId);
		users.add(user);
		logger.info("User created with id: {} - Total users: {}", newId, users.size());
		logger.debug("Created user details: {}", user);
		return user;
	}

	public Optional<User> updateUser(Long id, User updatedUser) {
		logger.debug("Attempting to update user with id: {}", id);
		Optional<User> result = getUserById(id).map(existingUser -> {
			logger.debug("Updating user fields for id: {}", id);
			existingUser.setFirstname(updatedUser.getFirstname());
			existingUser.setLastname(updatedUser.getLastname());
			existingUser.setEmail(updatedUser.getEmail());
			existingUser.setPhone(updatedUser.getPhone());
			existingUser.setStatus(updatedUser.getStatus());
			logger.info("User updated successfully with id: {}", id);
			logger.debug("Updated user details: {}", existingUser);
			return existingUser;
		});
		if (result.isEmpty()) {
			logger.warn("Update failed - User not found with id: {}", id);
		}
		return result;
	}

	public boolean deleteUser(Long id) {
		logger.debug("Attempting to delete user with id: {}", id);
		boolean deleted = users.removeIf(user -> user.getId().equals(id));
		if (deleted) {
			logger.info("User deleted successfully with id: {} - Remaining users: {}", id, users.size());
		} else {
			logger.warn("Delete failed - User not found with id: {}", id);
		}
		return deleted;
	}
}
