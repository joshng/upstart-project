package upstart;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import upstart.util.Reflect;
import upstart.util.SelfType;

import java.util.function.Supplier;
import java.util.stream.Stream;

public interface UpstartApplicationBuilder<Self extends UpstartApplicationBuilder<Self>> extends SelfType<Self> {
  Self installModule(Module module);
}
