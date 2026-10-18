package com.mentorhub.web;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The request conflicts with the current state and nothing was changed: 409 with the given problem type and, when
 * given, Problem extension members (for example the id of the conflicting resource).
 */
public class ConflictException extends RuntimeException {

    private final URI type;
    private final Map<String, Object> extensions;

    public ConflictException(URI type, String detail) {
        this(type, detail, Map.of());
    }

    public ConflictException(URI type, String detail, Map<String, ?> extensions) {
        super(detail);
        this.type = type;
        this.extensions = Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
    }

    public URI type() {
        return type;
    }

    public String detail() {
        return getMessage();
    }

    /** Members added to the problem next to the standard ones; empty when there are none. */
    public Map<String, Object> extensions() {
        return extensions;
    }
}
