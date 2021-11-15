package upstart.aws;

import upstart.util.concurrent.Promise;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class PublisherTransformer implements AsyncResponseTransformer<GetObjectResponse, SdkPublisher<ByteBuffer>> {
  private volatile Promise<SdkPublisher<ByteBuffer>> promise;

  @Override
  public CompletableFuture<SdkPublisher<ByteBuffer>> prepare() {
    return promise = new Promise<>();
  }

  @Override
  public void onResponse(GetObjectResponse response) {
  }

  @Override
  public void onStream(SdkPublisher<ByteBuffer> publisher) {
    promise.complete(publisher);
  }

  @Override
  public void exceptionOccurred(Throwable error) {
    promise.completeExceptionally(error);
  }
}
