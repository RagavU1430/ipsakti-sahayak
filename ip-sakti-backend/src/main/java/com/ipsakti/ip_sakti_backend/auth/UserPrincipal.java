package com.ipsakti.ip_sakti_backend.auth;

import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class UserPrincipal implements Principal, Authentication {

    private final UUID id;
    private final String externalAuthId;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;
    private boolean authenticated = true;

    public UserPrincipal(UUID id, String externalAuthId, String email, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.externalAuthId = externalAuthId;
        this.email = email;
        this.authorities = (authorities != null) ? authorities : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public static UserPrincipal of(UUID id, String externalAuthId, String email) {
        return new UserPrincipal(id, externalAuthId, email, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    public UUID getId() {
        return id;
    }

    public String getExternalAuthId() {
        return externalAuthId;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String getName() {
        return (externalAuthId != null) ? externalAuthId : (id != null ? id.toString() : "anonymous");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.unmodifiableCollection(authorities);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return this;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }
}
