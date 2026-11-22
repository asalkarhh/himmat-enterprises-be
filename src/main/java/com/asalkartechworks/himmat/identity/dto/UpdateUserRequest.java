package com.asalkartechworks.himmat.identity.dto;

import com.asalkartechworks.himmat.identity.model.UserRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;

    @Size(min = 2, max = 160, message = "Display name must be between 2 and 160 characters")
    private String displayName;

    private UserRole role;

    private Boolean isActive;
}
