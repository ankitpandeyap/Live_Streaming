package com.robspecs.live.dto;

import java.util.Objects;



import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordRequest {

	@NotBlank(message = "Email cannot be empty")
	@Email(message = "Invalid email format")
	private String email;

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public ForgotPasswordRequest(
			@NotBlank(message = "Email cannot be empty") @Email(message = "Invalid email format") String email) {
		super();
		this.email = email;
	}

	public ForgotPasswordRequest() {
		super();
		// TODO Auto-generated constructor stub
	}

	@Override
	public int hashCode() {
		return Objects.hash(email);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		ForgotPasswordRequest other = (ForgotPasswordRequest) obj;
		return Objects.equals(email, other.email);
	}

}

