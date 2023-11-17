package upstart.aws.test.localstack;

import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.localstack.LocalStackContainer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
@ExtendWith(UpstartAwsLocalStackExtension.class)
@Inherited
public @interface AwsLocalStackTest {
  String DEFAULT_LOCALSTACK_IMAGE = "localstack/localstack:1.4.0";
  LocalStackContainer.Service[] value();

  String image() default "";
}
