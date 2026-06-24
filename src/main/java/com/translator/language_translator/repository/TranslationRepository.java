package com.translator.language_translator.repository;

import com.translator.language_translator.model.TranslationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TranslationRepository extends JpaRepository<TranslationRecord, Long> {

    // Updated to sort by ID, which exists in all records and ensures compatibility
    List<TranslationRecord> findByUsernameOrderByIdDesc(String username);

    // Added to support your new "Clear All" history feature
    @Transactional
    void deleteByUsername(String username);
}