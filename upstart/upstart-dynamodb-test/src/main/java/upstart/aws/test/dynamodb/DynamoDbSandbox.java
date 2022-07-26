package upstart.aws.test.dynamodb;

import org.junit.jupiter.api.Disabled;
import upstart.UpstartApplication;

@LocalDynamoDbTest
@Disabled
public abstract class DynamoDbSandbox {

  protected abstract UpstartApplication getApplication();

}
