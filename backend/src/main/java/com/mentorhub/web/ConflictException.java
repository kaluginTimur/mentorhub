package com.mentorhub.web;

import java.net.URI;

/** The request conflicts with the current state and nothing was changed: 409 with the given problem type. */
public class ConflictException extends RuntimeException {

    private final URI type;

    public ConflictException(URI type, String detail) {
        super(detail);
        this.type = type;
    }

    public URI type() {
        return type;
    }

    public String detail() {
        return getMessage();
    }
}
