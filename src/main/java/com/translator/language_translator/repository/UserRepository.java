package com.translator.language_translator.repository;

import com.translator.language_translator.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {
    // Spring Data JPA magically writes the SQL for this method!
    Optional<AppUser> findByUsername(String username);
}