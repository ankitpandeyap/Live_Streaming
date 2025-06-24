package com.robspecs.live.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {

	@NotBlank(message = "Token cannot be empty")
	private String token;

	@NotBlank(message = "New password cannot be empty")
	@Size(min = 8, message = "Password must be at least 8 characters long")
	// Consider adding more complex password validation rules if needed
	private String newPassword;

	public ResetPasswordRequest() {
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getNewPassword() {
		return newPassword;
	}

	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}

}
