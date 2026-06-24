package com.translator.language_translator.repository;

import com.translator.language_translator.model.TranslationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface TranslationRepository extends JpaRepository<TranslationRecord, Long> {
    // Corrected method name to use Id for reliable chronological sorting
    List<TranslationRecord> findByUsernameOrderByIdDesc(String username);

    @Transactional
    void deleteByUsername(String username);
}