package com.asalkartechworks.himmat.identity.dto;

import com.asalkartechworks.himmat.identity.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {

    private Long id;
    private String username;
    private String displayName;
    private UserRole role;
    private boolean isActive;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Set<String> permissions;
}
