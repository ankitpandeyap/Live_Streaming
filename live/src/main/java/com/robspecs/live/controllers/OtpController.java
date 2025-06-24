package com.robspecs.live.controllers;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.robspecs.live.exceptions.EmailSendingException;
import com.robspecs.live.exceptions.InvalidOtpException;
import com.robspecs.live.exceptions.OtpCooldownException;
import com.robspecs.live.exceptions.TooManyOtpAttemptsException;
import com.robspecs.live.service.MailService;
import com.robspecs.live.service.OtpService;



@RestController
@RequestMapping("/api/auth/otp")
public class OtpController {

    private final OtpService otpService;
    private final MailService mailService;
    private static final Logger logger = LoggerFactory.getLogger(OtpController.class);

    public OtpController(OtpService otpService, MailService mailService) {
        this.otpService = otpService;
        this.mailService = mailService;
        logger.debug("OtpController initialized");
    }

    @PostMapping("/request")
    public ResponseEntity<?> requestOtp(@RequestParam String email) {
        logger.info("Received OTP request for email: {}", email);
        try {
            String otp = otpService.generateOtp(email); // This can throw OtpCooldownException or EncryptionDecryptionException
            logger.debug("Generated OTP: {} for email: {}", otp, email);
            mailService.sendOtpEmail(email, otp); // This can throw EmailSendingException
            logger.info("OTP sent to email: {}", email);
            return ResponseEntity.ok("OTP sent to " + email);
        } catch (OtpCooldownException e) {
            logger.warn("OTP request blocked for email {}: {}", email, e.getMessage());
            // @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS) on OtpCooldownException handles status
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (EmailSendingException e) {
            logger.error("Failed to send OTP email to {}: {}", email, e.getMessage(), e);
            // @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) on EmailSendingException handles status
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send OTP email: " + e.getMessage());

        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("Unexpected error during OTP request for email {}: {}", email, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Failed to request OTP: An unexpected error occurred.");
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestParam String email, @RequestParam String otp) {
        logger.info("Received OTP verification request for email: {}", email);
        logger.debug("Attempting to verify OTP: {} for email: {}", otp, email);
        try {
            // This call will throw an exception if validation fails
            otpService.validateOtp(email, otp);

            // If no exception is thrown, it means OTP is valid.
            logger.info("OTP verified successfully for email: {}", email);
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("verified", true);
            successResponse.put("message", "OTP verified successfully!"); // Consistent success message
            return ResponseEntity.ok(successResponse); // Return JSON object
        } catch (InvalidOtpException e) {
            logger.warn("OTP verification failed for email {}: {}", email, e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>(); // Changed to Object for consistency
            errorResponse.put("message", e.getMessage());
            errorResponse.put("verified", false);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (TooManyOtpAttemptsException e) {
            logger.warn("OTP verification failed for email {}: {}", email, e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>(); // Changed to Object for consistency
            errorResponse.put("message", e.getMessage());
            errorResponse.put("verified", false);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);

        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("Unexpected error during OTP verification for email {}: {}", email, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>(); // Changed to Object for consistency
            errorResponse.put("message", "OTP verification failed: An unexpected error occurred.");
            errorResponse.put("verified", false);
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}

