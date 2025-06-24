package com.robspecs.live.service;

import com.robspecs.live.dto.RegistrationDTO;
import com.robspecs.live.dto.ResetPasswordRequest;
import com.robspecs.live.entities.User;

public interface AuthService {

	User registerNewUser(RegistrationDTO regDTO);

	void processForgotPassword(String email); // <--- ADD THIS METHOD

	void resetPassword(ResetPasswordRequest request); // <--- ADD THIS METHOD
}
