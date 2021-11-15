package upstart.test.kafka;

import upstart.cluster.test.ZookeeperExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
@ExtendWith(ZookeeperExtension.class)
@ExtendWith(KafkaExtension.class)
public @interface LocalKafkaTest {
}
