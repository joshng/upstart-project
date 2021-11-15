package io.upstartproject.avrocodec.s3;


import io.upstartproject.avrocodec.SchemaFingerprint;
import upstart.aws.s3.test.MockS3;
import upstart.aws.s3.test.MockS3Test;
import io.upstartproject.avrocodec.SchemaDescriptor;
import io.upstartproject.avrocodec.SchemaRepo;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;

@MockS3Test(initialBuckets = S3SchemaRepoTest.TEST_REPO_BUCKET)
@Disabled
class S3SchemaRepoTest {

  public static final String TEST_REPO_BUCKET = "test-schema-repo";

  @Test
  void startRepo(MockS3 s3) {
    S3AsyncClient client = S3AsyncClient.builder().endpointOverride(s3.getEndpointUri()).region(Region.US_EAST_1).credentialsProvider(AnonymousCredentialsProvider.create()).build();
    client.putBucketVersioning(b -> b.bucket(TEST_REPO_BUCKET).versioningConfiguration(v -> v.status(BucketVersioningStatus.ENABLED))).join();
    S3SchemaRepo repo = new S3SchemaRepo(new S3SchemaRepo.S3RepoConfig() {
      @Override
      public String repoBucket() {
        return TEST_REPO_BUCKET;
      }

      @Override
      public String repoPath() {
        return "repo-root";
      }
    }, () -> client);

    repo.startUp(new SchemaRepo.SchemaListener() {
      @Override
      public void onSchemaAdded(SchemaDescriptor schema) {

        System.out.println("added: " + schema);
      }

      @Override
      public void onSchemaRemoved(SchemaFingerprint fingerprint) {
        System.out.println("removed: " + fingerprint);
      }
    }).join();
  }
}