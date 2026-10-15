package com.mentorhub.web;

/**
 * A resource that does not exist or that the caller may not access. Both respond 404 with the same body, so the
 * response never reveals whether an inaccessible resource exists.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Not found");
    }

    public NotFoundException(String message) {
        super(message);
    }
}
