package upstart.web;

import upstart.config.annotations.ConfigPath;

@ConfigPath("upstart.web.server")
public record WebServerConfig(
        String host,
        int port
) {}
