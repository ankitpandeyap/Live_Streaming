package com.robspecs.live.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.robspecs.live.dto.UserDTO;
import com.robspecs.live.dto.UserProfileDTO;
import com.robspecs.live.entities.User;
import com.robspecs.live.service.UserService;



@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		super();
		this.userService = userService;
	}

	@GetMapping("/all")
	public ResponseEntity<List<UserDTO>> getAllUsers(@AuthenticationPrincipal User currentUser) {
		List<UserDTO> users = userService.getAllUsers(currentUser);
		return ResponseEntity.ok(users);
	}

	@GetMapping("/me")
	public ResponseEntity<UserProfileDTO> getUserProfile(@AuthenticationPrincipal User currentUser) {
		if (currentUser == null) {
			// log.warn("Attempted to fetch user profile but no authenticated user found.
			// This should be caught by Spring Security.");
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		// log.info("Fetching profile for authenticated user: {}",
		// currentUser.getUserName());
		UserProfileDTO userProfile = userService.getUserProfile(currentUser.getUserName());
		return ResponseEntity.ok(userProfile);
	}

}
