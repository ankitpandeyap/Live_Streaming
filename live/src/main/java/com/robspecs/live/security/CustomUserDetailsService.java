package com.robspecs.live.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.robspecs.live.entities.User;
import com.robspecs.live.repository.UserRepository;

@Component
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;
	private final static Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

	@Autowired
	public CustomUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
		logger.debug("loadUserByUsername called for usernameOrEmail: {}", usernameOrEmail);
		User currentUser = userRepository.findByEmailOrUserName(usernameOrEmail).orElseThrow(() -> {
			logger.warn("User not found for email or Username: {}", usernameOrEmail);
			return new UsernameNotFoundException("emailor Username  not found in DB " + usernameOrEmail);
		});

		// The 'currentUser' object (your User entity) now directly implements
		// UserDetails
		logger.debug("User found for usernameOrEmail: {}. Returning User entity as UserDetails.", usernameOrEmail);
		return currentUser; // <-- Simple return of your User entity!
	}
}