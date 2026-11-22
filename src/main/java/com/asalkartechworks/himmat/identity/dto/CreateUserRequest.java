package com.asalkartechworks.himmat.identity.dto;

import com.asalkartechworks.himmat.identity.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 80, message = "Username must be between 3 and 80 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username must contain only alphanumeric characters, underscores, dots, or hyphens")
    private String username;

    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 160, message = "Display name must be between 2 and 160 characters")
    private String displayName;

    @NotNull(message = "Role is required")
    private UserRole role;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must be at least 12 characters long")
    private String password;
}
