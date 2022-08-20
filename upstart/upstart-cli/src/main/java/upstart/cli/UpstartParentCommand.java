package upstart.cli;

import picocli.CommandLine;

public abstract class UpstartParentCommand extends UpstartContextOptions {
  public void executeMain(String... args) {
    new CommandLine(this).execute(args);
  }
}
