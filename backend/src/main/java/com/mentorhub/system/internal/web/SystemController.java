package com.mentorhub.system.internal.web;

import com.mentorhub.api.SystemApi;
import com.mentorhub.api.model.ClientConfig;
import com.mentorhub.api.model.SystemInfo;
import com.mentorhub.users.NoPermissionRequired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SystemController implements SystemApi {

    static final String SCOPE = "openid profile email";

    private final String name;
    private final String version;
    private final ClientProperties client;

    SystemController(
            @Value("${spring.application.name}") String name,
            ObjectProvider<BuildProperties> buildProperties,
            ClientProperties client) {
        this.name = name;
        // build-info.properties is produced by the Maven build; absent when running from an IDE
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null ? build.getVersion() : "dev";
        this.client = client;
    }

    @Override
    @NoPermissionRequired
    public SystemInfo getSystemInfo() {
        return new SystemInfo(name, version);
    }

    @Override
    @NoPermissionRequired
    public ClientConfig getClientConfig() {
        // TODO(invitation-emails step 4): read from MailDelivery.isEnabled().
        return new ClientConfig(client.issuerUri(), client.clientId(), SCOPE, false);
    }
}
