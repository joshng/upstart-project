package upstart.config;

import upstart.config.annotations.ConfigPath;

import java.time.Instant;
import java.time.ZonedDateTime;

@ConfigPath("git.status")
public interface GitContext {
  String branch();
  String commit();
  String message();
  boolean dirty();
  ZonedDateTime timestamp();


  class GitContextModule extends UpstartModule {
    @Override
    protected void configure() {
      bindConfig("git.status", GitContext.class);
    }
  }
}
