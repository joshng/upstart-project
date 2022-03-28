package upstart;

import com.google.inject.Module;
import upstart.util.SelfType;

public interface UpstartApplicationBuilder<Self extends UpstartApplicationBuilder<Self>> extends SelfType<Self> {
  Self installModule(Module module);
}
