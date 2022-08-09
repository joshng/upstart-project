package upstart.dynamodb;

import upstart.config.annotations.ConfigPath;

import java.time.Duration;

@ConfigPath("upstart.dynamodb")
public interface DynamoDbConfig {
  Duration tableCreationPollPeriod();
}
