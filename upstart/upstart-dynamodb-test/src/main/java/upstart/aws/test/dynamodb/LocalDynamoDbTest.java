package upstart.aws.test.dynamodb;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@EnabledOnOs(OS.MAC) // Amazon's LocalDynamoDB lib causes problems in some docker environments due to native sqlite4j deps
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith(LocalDynamoDbExtension.class)
public @interface LocalDynamoDbTest {
}
