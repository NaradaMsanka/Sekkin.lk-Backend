package com.cosmetic.ecommerce.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@CrossOrigin
public class FileUploadController {

    private final Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();

    public FileUploadController() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize upload folder", e);
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload"));
        }

        try {
            String originalName = file.getOriginalFilename();
            String ext = ".webp";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
            }
            String fileName = "prod-" + UUID.randomUUID().toString().substring(0, 12) + ext;
            Path targetLocation = uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "http://localhost:8080/uploads/" + fileName;
            return ResponseEntity.ok(Map.of("url", fileUrl, "fileName", fileName));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not upload file: " + ex.getMessage()));
        }
    }
}
