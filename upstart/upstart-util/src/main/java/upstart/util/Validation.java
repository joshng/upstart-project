package upstart.util;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import upstart.util.collect.MoreStreams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Validation {
  public static Validation success() {
    return SUCCESS;
  }

  public static Validation withErrors(Stream<String> errors) {
//        return errors.reduce(SUCCESS, Validation::addError, Validation::merge); // potentially creates a lot of garbage?
    return MoreStreams.foldLeft(SUCCESS, errors, Validation::addError);
  }

  public static Validation withErrors(String... errors) {
    return MoreStreams.foldLeft(SUCCESS, Arrays.stream(errors), Validation::addError);
  }

  public static Validation collect(Stream<Validation> validations) {
//        return validations.reduce(Validation::merge).orElse(SUCCESS); // potentially creates a lot of garbage?
    return MoreStreams.foldLeft(SUCCESS, validations, Validation::merge);
  }

  public Validation addError(String format, Object... args) {
    return addError(Strings.lenientFormat(format, args));
  }

  public Validation confirm(boolean condition, String message, Object... args) {
    return condition ? this : addError(message, args);
  }

  public void throwIllegalArguments() {
    throwFailures(messages -> new IllegalArgumentException(messages.stream()
            .collect(Collectors.joining("\n", "Validation failure(s):\n", ""))));
  }

  public abstract Validation addError(String message);

  public abstract Validation merge(Validation other);

  public abstract boolean hasErrors();

  public abstract List<String> getErrors();

  public abstract <E extends Exception> void throwFailures(Function<? super Collection<String>, ? extends RuntimeException> exceptionBuilder) throws E;

  private static final Validation SUCCESS = new Validation() {
    @Override
    public Validation addError(String message) {
      return new Failed(message);
    }

    @Override
    public Validation merge(Validation other) {
      return other;
    }

    @Override
    public boolean hasErrors() {
      return false;
    }

    @Override
    public List<String> getErrors() {
      return ImmutableList.of();
    }

    @Override
    public <E extends Exception> void throwFailures(Function<? super Collection<String>, ? extends RuntimeException> exceptionBuilder) throws E {
    }
  };

  private static class Failed extends Validation {
    private final List<String> errors = new ArrayList<>();

    private Failed(String message) {
      errors.add(message);
    }

    @Override
    public synchronized Validation addError(String message) {
      errors.add(message);
      return this;
    }

    @Override
    public <E extends Exception> void throwFailures(Function<? super Collection<String>, ? extends RuntimeException> exceptionBuilder) throws E {
      throw exceptionBuilder.apply(errors);
    }

    @Override
    public boolean hasErrors() {
      return true;
    }

    @Override
    public List<String> getErrors() {
      return errors;
    }

    @Override
    public Validation merge(Validation other) {
      if (other.hasErrors()) {
        synchronized (this) {
          errors.addAll(other.getErrors());
        }
      }
      return this;
    }
  }
}

