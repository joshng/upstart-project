package upstart.test.kafka;


import upstart.cluster.test.ZookeeperFixture;
import upstart.test.BaseSingletonParameterResolver;
import upstart.test.ExtensionContexts;
import upstart.util.reflect.Reflect;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.stream.Stream;

public class KafkaExtension extends BaseSingletonParameterResolver<EphemeralKafkaBroker> implements BeforeEachCallback, AfterEachCallback {
    public KafkaExtension() {
        super(EphemeralKafkaBroker.class);
    }

    @Override
    public EphemeralKafkaBroker createContext(ExtensionContext extensionContext) throws Exception {
        return EphemeralKafkaBroker.create(ZookeeperFixture.getInstance(extensionContext).connectString());
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        EphemeralKafkaBroker broker = getOrCreateContext(context);
        broker.start().get();
        NewTopic[] newTopics = ExtensionContexts.findRepeatableTestAnnotations(LocalKafkaTopic.class, Reflect.LineageOrder.SuperclassBeforeSubclass, context)
                .flatMap(topicAnnotation -> Stream.of(topicAnnotation.value()).map(topic -> EphemeralKafkaBroker.newTopic(topic, topicAnnotation.partitions())))
                .toArray(NewTopic[]::new);
        if (newTopics.length > 0) broker.createTopics(newTopics);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        getExistingContext(context).ifPresent(EphemeralKafkaBroker::stop);
    }
}

