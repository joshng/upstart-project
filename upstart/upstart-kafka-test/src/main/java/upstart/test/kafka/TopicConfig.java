package upstart.test.kafka;

import java.util.function.Consumer;

public interface TopicConfig extends Consumer<TopicAdmin.NewTopicBuilder> {
  TopicConfig Compacted = TopicAdmin.NewTopicBuilder::compacted;
}
