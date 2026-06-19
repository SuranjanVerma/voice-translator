package com.translator.language_translator.repository;

import com.translator.language_translator.model.TranslationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranslationRepository extends JpaRepository<TranslationRecord, Long> {

    // Spring Data JPA magically writes the SQL query for this based on the method name!
    List<TranslationRecord> findByUsernameOrderByTimestampDesc(String username);
}