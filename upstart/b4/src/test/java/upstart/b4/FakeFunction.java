package upstart.b4;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.config.UpstartModule;
import org.immutables.value.Value;
import org.mockito.Mockito;

public interface FakeFunction extends B4Function<FakeFunction.FakeConfig> {
  @Value.Immutable
  @JsonDeserialize(as = ImmutableFakeConfig.class)
  interface FakeConfig {
    String string();
    int number();
  }

  class GuiceModule extends UpstartModule {
    @Override
    protected void configure() {
      bind(FakeFunction.class).toInstance(Mockito.mock(FakeFunction.class));
    }
  }
}
