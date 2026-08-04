package com.example.demo.service;

import com.example.demo.model.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * User service interface.
 * In this example-mock project, the real implementation is never used —
 * the service is always mocked via @MockBean in the test class.
 */
@Service
public class UserService {

	public List<User> getAllUsers() {
		throw new UnsupportedOperationException("Real service - should be mocked in tests");
	}

	public Optional<User> getUserById(Long id) {
		throw new UnsupportedOperationException("Real service - should be mocked in tests");
	}

	public User createUser(User user) {
		throw new UnsupportedOperationException("Real service - should be mocked in tests");
	}

	public Optional<User> updateUser(Long id, User updatedUser) {
		throw new UnsupportedOperationException("Real service - should be mocked in tests");
	}

	public boolean deleteUser(Long id) {
		throw new UnsupportedOperationException("Real service - should be mocked in tests");
	}
}
