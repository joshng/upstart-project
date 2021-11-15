package upstart.cluster.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@ExtendWith({ZookeeperExtension.class, UpstartClusterExtension.class})
public @interface UpstartClusterTest {
  int DEFAULT_NODE_COUNT = 3;

  int nodeCount() default DEFAULT_NODE_COUNT;
}
