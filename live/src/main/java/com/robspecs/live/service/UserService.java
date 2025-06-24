package com.robspecs.live.service;

import java.util.List;
import java.util.Optional;

import com.robspecs.live.dto.UserDTO;
import com.robspecs.live.dto.UserProfileDTO;
import com.robspecs.live.entities.User;



public interface UserService {
	public List<UserDTO> getAllUsers(User user);

	Optional<User> findByUserName(String userName);

	UserProfileDTO getUserProfile(String username);
}

