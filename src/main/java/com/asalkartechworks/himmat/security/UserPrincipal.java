package com.asalkartechworks.himmat.security;

import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.model.Permission;
import com.asalkartechworks.himmat.identity.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final UserRole role;
    private final boolean active;
    private final Long version;
    private final Set<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(AppUser user) {
        Set<String> perms = Permission.getPermissionsForRole(user.getRole());
        List<GrantedAuthority> authorities = new ArrayList<>();
        // Role authority with standard ROLE_ prefix
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        // Permission authorities
        for (String perm : perms) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }

        return UserPrincipal.builder()
                .id(user.getId())
                .username(user.getUsername())
                .passwordHash(user.getPasswordHash())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .active(user.isActive())
                .version(user.getVersion())
                .permissions(perms)
                .authorities(authorities)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
