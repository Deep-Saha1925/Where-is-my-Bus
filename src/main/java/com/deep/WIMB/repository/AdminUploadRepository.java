package com.deep.WIMB.repository;

import com.deep.WIMB.model.AdminUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUploadRepository extends JpaRepository<AdminUpload, Long> {

    Optional<AdminUpload> findByKind(String kind);
}
