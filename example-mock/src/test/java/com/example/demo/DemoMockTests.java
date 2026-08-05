package com.example.demo;

import com.example.demo.model.User;
import com.example.demo.service.UserService;
import io.scanrest.autoconfigure.EnableScanRest;
import io.scanrest.autoconfigure.ScanRestTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Example: ScanRest with mocked services (MOCKMVC mode).
 *
 * The real UserService is replaced by a Mockito mock via @MockitoBean.
 * The controller layer runs for real, but the service layer is fully controlled
 * by the test — no database, no real business logic.
 *
 * This demonstrates:
 * - @MockitoBean to replace the service with a mock
 * - MOCKMVC mode (no HTTP server needed, tests run via Spring MockMvc)
 * - @TestFactory for individual JUnit test results per YAML test case
 */
@SpringBootTest
@EnableScanRest
class DemoMockTests {

	@MockitoBean
	private UserService userService;

	@Autowired
	private ScanRestTestRunner scanRestRunner;

	private final User john = new User(1L, "John", "Doe", "john@example.com", "1234567890", "ACTIVE");
	private final User jane = new User(1L, "Jane", "Doe", "jane@example.com", "9876543210", "ACTIVE");

	@BeforeEach
	void setupMocks() {
		// GET all users
		when(userService.getAllUsers()).thenReturn(List.of(john));

		// GET user by ID
		when(userService.getUserById(1L)).thenReturn(Optional.of(john));
		when(userService.getUserById(99999L)).thenReturn(Optional.empty());

		// POST create user - return a user with ID assigned
		when(userService.createUser(any(User.class))).thenAnswer(invocation -> {
			User input = invocation.getArgument(0);
			input.setId(1L);
			return input;
		});

		// PUT update user
		when(userService.updateUser(eq(1L), any(User.class))).thenAnswer(invocation -> {
			User input = invocation.getArgument(1);
			input.setId(1L);
			return Optional.of(input);
		});
		when(userService.updateUser(eq(99999L), any(User.class))).thenReturn(Optional.empty());

		// DELETE user
		when(userService.deleteUser(1L)).thenReturn(true);
		when(userService.deleteUser(99999L)).thenReturn(false);
	}

	@TestFactory
	Collection<DynamicTest> apiTests() {
		return scanRestRunner.toDynamicTests();
	}

}
