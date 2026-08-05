package com.example.auth.service;

import com.example.auth.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory authentication service using static maps.
 * Stores hardcoded users and active tokens.
 */
@Service
public class AuthService {

	// Static in-memory storage (survives across requests)
	private static final Map<String, String> USERS = new ConcurrentHashMap<>();
	private static final Map<String, UserProfile> TOKENS = new ConcurrentHashMap<>();

	static {
		// Seed with test users
		USERS.put("admin", "admin123");
		USERS.put("user", "password");
	}

	/**
	 * Authenticate user and generate a token.
	 */
	public String login(String username, String password) {
		String storedPassword = USERS.get(username);
		if (storedPassword == null || !storedPassword.equals(password)) {
			return null; // Invalid credentials
		}

		// Generate token
		String token = UUID.randomUUID().toString();
		UserProfile profile = new UserProfile(username, username + "@example.com", "USER");
		TOKENS.put(token, profile);

		return token;
	}

	/**
	 * Validate token and return user profile.
	 */
	public UserProfile validateToken(String token) {
		return TOKENS.get(token);
	}

	/**
	 * Logout (invalidate token).
	 */
	public void logout(String token) {
		TOKENS.remove(token);
	}
}
