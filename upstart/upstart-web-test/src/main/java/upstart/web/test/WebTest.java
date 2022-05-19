package upstart.web.test;

import org.junit.jupiter.api.extension.ExtendWith;
import upstart.test.UpstartTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@ExtendWith(WebExtension.class)
@UpstartTest
public @interface WebTest {
  int port() default WebExtension.RANDOM_PORT;
}
