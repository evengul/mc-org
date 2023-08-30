package app.mcorg.permission.presentation.rest.entities;

import org.springframework.http.ResponseEntity;

public record GenericResponse(boolean success, String message) {
    public static ResponseEntity<GenericResponse> ok(String message) {
        return ResponseEntity.ok(new GenericResponse(true, message));
    }
}
