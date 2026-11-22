package org.xidian.program325.pojo;

public final class Account {
    private Account() {
    }

    public enum Role { USER, ADMIN }

    public record LoginRequest(String username, String password, Role role) {
    }

    public record SessionUser(long userId, String username, Role role) {
    }

    public record User(long userId, String username, String passwordHash, Role role) {
    }
}
