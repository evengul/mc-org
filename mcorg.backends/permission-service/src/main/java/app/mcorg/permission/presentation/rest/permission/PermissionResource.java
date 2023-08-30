package app.mcorg.permission.presentation.rest.permission;

import app.mcorg.permission.presentation.rest.entities.AddPermissionRequest;
import app.mcorg.permission.presentation.rest.entities.ChangeAuthorityRequest;
import app.mcorg.permission.presentation.rest.entities.RemovePermissionRequest;
import app.mcorg.permission.presentation.rest.entities.UserPermissionsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/permissions")
public interface PermissionResource extends PermissionDocumentation {
    @PutMapping("/{username}")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<Void>> addPermission(
            @PathVariable String username,
            @RequestBody
            @Valid
            AddPermissionRequest request);

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<Void>> removePermission(
            @PathVariable String username,
            @RequestBody
            @Valid
            RemovePermissionRequest request);

    @PatchMapping("/{username}")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<Void>> changeAuthority(
            @PathVariable String username,
            @RequestBody
            @Valid
            ChangeAuthorityRequest request);

    @GetMapping("/mine")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<UserPermissionsResponse>> getUserPermissions();
}
