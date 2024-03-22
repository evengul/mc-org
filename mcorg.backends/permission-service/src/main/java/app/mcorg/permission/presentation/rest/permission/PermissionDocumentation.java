package app.mcorg.permission.presentation.rest.permission;

import app.mcorg.permission.presentation.rest.entities.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

@Tag(name = "Permissions")
@SuppressWarnings("unused")
public interface PermissionDocumentation {
    @Operation(summary = "Add a permission for a user")
    CompletableFuture<ResponseEntity<Void>> addPermission(String username, AddPermissionRequest request);

    @Operation(summary = "Remove a permission for a user")
    CompletableFuture<ResponseEntity<Void>> removePermission(String username, RemovePermissionRequest request);

    @Operation(summary = "Change a permission authority for a user")
    CompletableFuture<ResponseEntity<Void>> changeAuthority(String username, ChangeAuthorityRequest request);

    @Operation(summary = "Retrieve all permissions of the signed in user")
    CompletableFuture<ResponseEntity<UserPermissionsResponse>> getUserPermissions();

    @Operation(summary = "Retrieve profile of the signed in user")
    CompletableFuture<ResponseEntity<MyProfileResponse>> getProfile();
}
