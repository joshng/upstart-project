package upstart.b4;

public interface B4Function<C> {
  void clean(C config, B4TaskContext context) throws Exception;

  void run(C config, B4TaskContext context) throws Exception;

  void cancel();

  default boolean runOnSeparateThread() {
    return true;
  }

  enum Verbosity {
    Quiet(false, false),
    Info(true, false),
    Verbose(true, true),
    Debug(true, true);

    public final boolean logCommands;
    public final boolean logOutput;

    Verbosity(boolean logCommands, boolean logOutput) {
      this.logCommands = logCommands;
      this.logOutput = logOutput;
    }

    public boolean isEnabled(Verbosity level) {
      return this.compareTo(level) >= 0;
    }

    public boolean isSuppressed(Verbosity level) {
      return !isEnabled(level);
    }

    public boolean verboseEnabled() {
      return isEnabled(Verbose);
    }
  }
}
