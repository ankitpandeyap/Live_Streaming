package com.robspecs.live.repository; // Changed package

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.robspecs.live.entities.User; // Changed import

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Boolean existsByEmail(String email);

	@Query("SELECT u FROM User u WHERE u.email = :usernameOrEmail OR u.userName = :usernameOrEmail")
	Optional<User> findByEmailOrUserName(@Param("usernameOrEmail") String usernameOrEmail);

}