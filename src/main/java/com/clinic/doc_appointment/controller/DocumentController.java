package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.DocumentResponse;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.document.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Document Upload", description = "Endpoints for uploading and downloading encrypted medical files")
@SecurityRequirement(name = "Bearer Authentication")
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "Upload a document for an appointment")
    @PostMapping(value = "/appointments/{appointmentId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @PathVariable String appointmentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
            
        // We figure out the role based on what type of user is logged in
        Role role = Role.fromAuthority(userPrincipal.getRole());
        String userId = userPrincipal.getId();
        
        DocumentResponse response = documentService.uploadDocument(appointmentId, userId, role, file, documentType);
        
        return ResponseEntity.ok(ApiResponse.<DocumentResponse>builder()
                .success(true)
                .message("File uploaded successfully")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Get all documents for an appointment")
    @GetMapping("/appointments/{appointmentId}/documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getAppointmentDocuments(
            @PathVariable String appointmentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
            
        Role role = Role.fromAuthority(userPrincipal.getRole());
        String userId = userPrincipal.getId();
        
        List<DocumentResponse> response = documentService.getDocumentsForAppointment(appointmentId, userId, role);
        
        return ResponseEntity.ok(ApiResponse.<List<DocumentResponse>>builder()
                .success(true)
                .message("Documents fetched successfully")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Stream a document to the client")
    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
            
        Role role = Role.fromAuthority(userPrincipal.getRole());
        String userId = userPrincipal.getId();

        // Single DB call: validates auth + returns metadata + streams resource together
        DocumentService.DocumentDownload download = documentService.downloadDocument(documentId, userId, role);

        String contentType = download.metadata().getFileType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                // 'attachment' tells the HTTP client: save this as a file (not display inline)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.metadata().getFileName() + "\"")
                .body(download.resource());
    }
    
    @Operation(summary = "Delete an uploaded document")
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<Object>> deleteDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
            
        Role role = Role.fromAuthority(userPrincipal.getRole());
        String userId = userPrincipal.getId();
        
        documentService.deleteDocument(documentId, userId, role);
        
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("File deleted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }
}
