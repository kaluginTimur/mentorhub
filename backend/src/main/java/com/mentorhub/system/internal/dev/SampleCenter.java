package com.mentorhub.system.internal.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Brings the dev database back to the sample center data (DEV-1, DEV-2). */
@Component
@Profile("dev")
class SampleCenter {

    // Stub until the restore on start is implemented: does nothing yet.
    public void restore() {}
}
