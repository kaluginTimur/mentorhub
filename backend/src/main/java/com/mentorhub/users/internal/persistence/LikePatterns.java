package com.mentorhub.users.internal.persistence;

/** Patterns for {@code ilike ... escape '\'} in which {@code %}, {@code _} and {@code \} match themselves. */
final class LikePatterns {

    private LikePatterns() {}

    static String contains(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
