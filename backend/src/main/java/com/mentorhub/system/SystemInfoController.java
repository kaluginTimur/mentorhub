package com.mentorhub.system;

import com.mentorhub.api.SystemApi;
import com.mentorhub.api.model.ClientConfig;
import com.mentorhub.api.model.SystemInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class SystemInfoController implements SystemApi {

    private final String name;
    private final String version;

    SystemInfoController(
            @Value("${spring.application.name}") String name, ObjectProvider<BuildProperties> buildProperties) {
        this.name = name;
        // build-info.properties is produced by the Maven build; absent when running from an IDE
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null ? build.getVersion() : "dev";
    }

    @Override
    public SystemInfo getSystemInfo() {
        return new SystemInfo(name, version);
    }

    // Temporary stub until step 4 of identity-foundation implements the client configuration.
    @Override
    public ClientConfig getClientConfig() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
