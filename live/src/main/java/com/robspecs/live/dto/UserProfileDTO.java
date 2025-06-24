package com.robspecs.live.dto;

import java.time.LocalDateTime;

public class UserProfileDTO {
	private String username;
	private String email;
	private LocalDateTime createdAt;
	// Add any other user-related fields you want to expose, e.g., fullName,
	// profilePictureUrl
	// Be mindful not to expose sensitive information like password hashes or OTPs.

	public UserProfileDTO() {
	}

	public UserProfileDTO(String username, String email, LocalDateTime createdAt) {
		this.username = username;
		this.email = email;
		this.createdAt = createdAt;
	}

	// Getters and Setters
	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	@Override
	public String toString() {
		return "UserProfileDTO{" + "username='" + username + '\'' + ", email='" + email + '\'' + ", createdAt="
				+ createdAt + '}';
	}
}
