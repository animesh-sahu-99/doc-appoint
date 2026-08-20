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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Document Upload", description = "Endpoints for uploading and downloading encrypted medical files")
@SecurityRequirement(name = "Bearer Authentication")
public class DocumentController {

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    private final DocumentService documentService;

    @Operation(summary = "Upload a document for an appointment")
    @PostMapping(value = "/appointments/{appointmentId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @PathVariable String appointmentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Caller caller = Caller.of(userPrincipal);

        DocumentResponse response = documentService.uploadDocument(
                appointmentId, caller.userId(), caller.role(), file, documentType);

        return ResponseEntity.ok(ApiResponse.success(response, "File uploaded successfully"));
    }

    @Operation(summary = "Get all documents for an appointment")
    @GetMapping("/appointments/{appointmentId}/documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getAppointmentDocuments(
            @PathVariable String appointmentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Caller caller = Caller.of(userPrincipal);

        List<DocumentResponse> response = documentService.getDocumentsForAppointment(
                appointmentId, caller.userId(), caller.role());

        return ResponseEntity.ok(ApiResponse.success(response, "Documents fetched successfully"));
    }

    @Operation(summary = "Stream a document to the client")
    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Caller caller = Caller.of(userPrincipal);

        // Single DB call: validates auth + returns metadata + streams resource together
        DocumentService.DocumentDownload download =
                documentService.downloadDocument(documentId, caller.userId(), caller.role());

        String contentType = download.metadata().getFileType();
        if (contentType == null || contentType.isBlank()) {
            contentType = FALLBACK_CONTENT_TYPE;
        }

        // ContentDisposition quotes and encodes the file name. Interpolating it into the header by
        // hand broke the moment a name contained a double quote, and offered no encoding for
        // non-ASCII names at all.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.metadata().getFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    @Operation(summary = "Delete an uploaded document")
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Caller caller = Caller.of(userPrincipal);

        documentService.deleteDocument(documentId, caller.userId(), caller.role());

        return ResponseEntity.ok(ApiResponse.success(null, "File deleted successfully"));
    }

    /**
     * The {@code (userId, role)} pair every method here needs.
     *
     * <p>Unpacking the principal was four identical two-line blocks. Naming the pair once keeps the
     * service signatures as they are while giving the conversion a single home.
     */
    private record Caller(String userId, Role role) {
        static Caller of(UserPrincipal principal) {
            return new Caller(principal.getId(), Role.fromAuthority(principal.getRole()));
        }
    }
}
