package upstart.aws.s3.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockS3Extension.class)
public @interface MockS3Test {
  int DEFAULT_PORT = 8011;
  String NO_DIRECTORY = "";

  int port() default DEFAULT_PORT;

  /**
   * Optional: specifies a directory to hold the files stored by this S3Mock instance.
   * <p/>
   * If this value is omitted, or assigned the empty-string ("")), files will only be held ephemerally in memory.
   */
  String fileDirectory() default NO_DIRECTORY;

  String[] initialBuckets() default {};
}
