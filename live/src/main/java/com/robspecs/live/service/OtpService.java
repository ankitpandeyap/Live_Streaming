package com.robspecs.live.service;

public interface OtpService {

	public String generateOtp(String email);

	public boolean validateOtp(String email, String otp);

}
