package upstart.b4.devops;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.cache.CacheBuilder;
import io.upstartproject.hojack.Size;
import io.upstartproject.hojack.SizeUnit;
import upstart.b4.B4Function;
import upstart.b4.B4TaskContext;
import upstart.b4.functions.MavenConfig;
import upstart.util.strings.MoreStrings;
import upstart.util.annotations.Tuple;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.Throttler;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.immutables.value.Value;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

public class NexusDownloadFunction implements B4Function<NexusDownloadFunction.DownloadConfig> {
  private final NexusCredentialStore credentials;
  private final Promise<Call> callPromise = new Promise<>();

  @Inject
  public NexusDownloadFunction(NexusCredentialStore credentials) {
    this.credentials = credentials;
  }

  @Override
  public void clean(DownloadConfig config, B4TaskContext context) throws Exception {
    context.effect("Clean: deleting", config.to().toString())
            .run(() -> Files.deleteIfExists(config.to()));
  }

  @Override
  public void run(DownloadConfig config, B4TaskContext context) throws Exception {

    NexusCredentialStore.Creds creds = config.credentials().orElseGet(() -> credentials.get(config.mavenRepoId()));
    String credential = Credentials.basic(creds.username(), creds.password());

    OkHttpClient client = new OkHttpClient.Builder()
//            .authenticator((route, response) -> {
//        if (response.request().header("Authorization") != null) return null; // authN failed, give up
//        return response.request().newBuilder().header("Authorization", credential).build();
//    })
            .cache(new Cache(new File("tmp/httpCache"), SizeUnit.GIGABYTES.toBytes(2)))
            .build();

    String description = config.to().toString() + "\n  from " + config.url();
    context.effect("Downloading from nexus:", description).run(() -> {
      Files.createDirectories(config.to().normalize().getParent());
      Request req = new Request.Builder()
              .get()
              .url(config.url())
              .header("Authorization", credential)
              .build();
      Call call = client.newCall(req);
      callPromise.complete(call);
      Response response = call.execute();
      try (ResponseBody body = response.body()) {
        if (!response.isSuccessful()) throw new IOException("Download returned status-code " + response.code());
        try (
                ReadableByteChannel in = Channels.newChannel(body.byteStream());
                FileChannel out = FileChannel.open(config.to(), CREATE, WRITE, TRUNCATE_EXISTING)
        ) {
          Throttler progressThrottler = new Throttler(5, TimeUnit.SECONDS);
          long totalBytes = body.contentLength();
          Size totalSize = Size.bytes(totalBytes);
          long pos = 0;
          long remainingBytes = totalBytes;
          while (remainingBytes > 0L) {
            context.yieldIfCanceled();
            long bytesWritten = out.transferFrom(in, pos, Math.min(524288, remainingBytes));
            pos += bytesWritten;
            remainingBytes -= bytesWritten;
            if (progressThrottler.tryAcquire()) {
              context.sayFormatted("%3.0f%% -- %s -- %d/%dkb", (double) pos / totalBytes * 100, description, SizeUnit.BYTES
                      .toKilobytes(pos), totalSize.toKilobytes());
            }
          }
        }
      }
    });
  }

  @Override
  public void cancel() {
    callPromise.thenAccept(Call::cancel);
  }

  static class NexusCredentialStore {
    private static final com.google.common.cache.Cache<String, Creds> CREDS = CacheBuilder.newBuilder().build();
    private final MavenConfig mavenConfig;
    private final B4TaskContext context;

    @Inject
    NexusCredentialStore(MavenConfig mavenConfig, B4TaskContext context) {
      this.mavenConfig = mavenConfig;
      this.context = context;
    }

    public Creds get(String repoId) {
      try {
        return CREDS.get(repoId, () -> {
          String args = String.format(
                  // this bizarre expression syntax, with the interior curly-braces, tricks the
                  // help:evaluate task into supporting multiple expressions in one invocation.
                  // (note that the beginning and end braces are omitted! :-P)
                  "help:evaluate -Dexpression=settings.servers.%1$s.username}\t"
                  + "${settings.servers.%1$s.password -DforceStdout -q",
                  repoId
          );
          String usernamePassword = context.getQuietly(() -> context.alwaysRunCommand(mavenConfig.mvnExecutable(), builder ->
                  builder.addSpaceSeparatedArgs(args)
                          .captureOutputString()
          ).outputString());
          return MoreStrings.splitAroundFirst(usernamePassword, '\t').map2(Creds::of);
        });
      } catch (ExecutionException e) {
        throw new RuntimeException("Exception retrieving nexus credentials for repository " + repoId, e);
      }
    }

    @Value.Immutable
    @Tuple
    @JsonDeserialize(as = ImmutableCreds.class)
    interface Creds {
      static Creds of(String username, String password) {
        return ImmutableCreds.of(username, password);
      }
      String username();
      String password();
    }
  }

  @Value.Immutable
  @JsonDeserialize(builder = ImmutableDownloadConfig.Builder.class)
  public interface DownloadConfig {
    String mavenExecutable();
    String mavenRepoId();
    Optional<NexusCredentialStore.Creds> credentials();
    URL url();
    Path to();
  }
}
