package com.robspecs.live.dto; // Changed package

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginDTO {

	@NotBlank(message = "Username or Email cannot be empty")
	@Size(min = 3, max = 50, message = "Username or Email must be between 3 and 50 characters")
	private String usernameOrEmail;

	@NotBlank(message = "Password cannot be empty")
	//@Size(min = 8, message = "Password must be at least 8 characters long") // You can uncomment this if needed
	private String password;

	// Getters and Setters (ensure they are present and match your field names)
	public String getUsernameOrEmail() {
		return usernameOrEmail;
	}

	public void setUsernameOrEmail(String usernameOrEmail) {
		this.usernameOrEmail = usernameOrEmail;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

}