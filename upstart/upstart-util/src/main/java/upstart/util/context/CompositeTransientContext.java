package upstart.util.context;

import com.google.common.collect.ImmutableList;
import upstart.util.exceptions.MultiException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;

public class CompositeTransientContext implements TransientContext {
  private final ImmutableList<? extends TransientContext> contexts;

  public CompositeTransientContext(Iterable<? extends TransientContext> contexts) {
    this.contexts = ImmutableList.copyOf(contexts);
  }

  public CompositeState enter() {
    return new CompositeState();
  }

  @Override public <T> T callInContext(Callable<T> callable) throws Exception {
    return wrapCallable(callable).call();
  }

  public <T> Callable<T> wrapCallable(Callable<T> callable) {
    Callable<T> wrapped = callable;
    for (TransientContext context : contexts) {
      wrapped = context.wrapCallable(wrapped);
    }
    return wrapped;
  }

  private class CompositeState implements State {
    private final Deque<State> states = new ArrayDeque<>(contexts.size());

    CompositeState() {
      try {
        for (TransientContext context : contexts) {
          states.push(context.enter());
        }
      } catch (RuntimeException e) {
        exit(MultiException.Empty.with(e));
      }
    }

    public void exit() {
      exit(MultiException.Empty);
    }

    private void exit(MultiException exception) {
      while (!states.isEmpty()) {
        try {
          states.pop().exit();
        } catch (RuntimeException e) {
          exception = exception.with(e);
        }
      }

      exception.throwRuntimeIfAny();
    }
  }
}
