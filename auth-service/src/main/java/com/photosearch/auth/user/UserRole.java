package com.photosearch.auth.user;

public enum UserRole {
    USER(0),
    ADMIN(1),
    SUPER_ADMIN(2),
    OWNER(3);

    private final int rank;

    UserRole(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public boolean canAffect(UserRole target) {
        return this.rank > target.rank;
    }
}
