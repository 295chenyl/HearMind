package com.dovidioai.security;

public final class UserContext {

    private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AuthUser user) {
        CURRENT.set(user);
    }

    public static AuthUser get() {
        return CURRENT.get();
    }

    public static Long getUserId() {
        AuthUser user = CURRENT.get();
        return user != null ? user.userId() : null;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record AuthUser(Long userId, String username) {
    }
}
