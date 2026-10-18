package com.mentorhub.web;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * A resource that does not exist or that the caller may not access. Both respond 404 with the same body, so the
 * response never reveals whether an inaccessible resource exists. A feature may give its 404 its own problem type
 * (for example a link that is invalid for any of several reasons); the default is {@link Problems#NOT_FOUND}.
 */
public class NotFoundException extends RuntimeException {

    private final URI type;
    private final @Nullable String detail;

    public NotFoundException() {
        super("Not found");
        this.type = Problems.NOT_FOUND;
        this.detail = null;
    }

    public NotFoundException(String message) {
        super(message);
        this.type = Problems.NOT_FOUND;
        this.detail = null;
    }

    /** A 404 with the given problem type and a detail that is sent to the client. */
    public NotFoundException(URI type, String detail) {
        super(detail);
        this.type = type;
        this.detail = detail;
    }

    public URI type() {
        return type;
    }

    /** The detail sent to the client; {@code null} for a plain not-found, whose message stays in the server. */
    public @Nullable String detail() {
        return detail;
    }
}
