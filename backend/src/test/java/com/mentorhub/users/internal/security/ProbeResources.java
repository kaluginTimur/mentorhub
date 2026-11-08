package com.mentorhub.users.internal.security;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** The resources behind {@link PermissionProbeController}; slice tests mock it to see whether a call reached them. */
@Component
class ProbeResources {

    void change(UUID id) {}

    void create(PermissionProbeController.ProbeBody body) {}
}
