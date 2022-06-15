package upstart.util.context;

import com.google.common.collect.ImmutableList;

import java.util.concurrent.Callable;

public class CompositeTransientContext implements TransientContext {
  private final TransientContext outer;
  private final TransientContext inner;

  public CompositeTransientContext(TransientContext outer, TransientContext inner) {
    this.outer = outer;
    this.inner = inner;
  }

  public CompositeState open() {

    return new CompositeState();
  }

  @Override public <T> T callInContext(Callable<T> callable) throws Exception {
    return wrapCallable(callable).call();
  }

  private class CompositeState implements State {
    private final State outerState;
    private final State innerState;

    CompositeState() {
      outerState = outer.open();
      try {
        innerState = inner.open();
      } catch (Throwable e) {
        closeOuterState(e);
        throw e;
      }
    }

    public void close() {
      try {
        innerState.close();
      } catch (Throwable e) {
        closeOuterState(e);
        throw e;
      }
      outerState.close();
    }

    private void closeOuterState(Throwable innerException) {
      try {
        outerState.close();
      } catch (Exception ex) {
        innerException.addSuppressed(ex);
      }
    }
  }
}
