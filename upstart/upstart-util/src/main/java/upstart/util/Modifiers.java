package upstart.util;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.function.Predicate;

public enum Modifiers implements Predicate<Class<?>> {
  Public(Modifier.PUBLIC),
  Private(Modifier.PRIVATE),
  Protected(Modifier.PROTECTED),
  Static(Modifier.STATIC),
  Final(Modifier.FINAL),
  Synchronized(Modifier.SYNCHRONIZED),
  Volatile(Modifier.VOLATILE),
  Transient(Modifier.TRANSIENT),
  Native(Modifier.NATIVE),
  Interface(Modifier.INTERFACE),
  Abstract(Modifier.ABSTRACT),
  Concrete(0) {
    protected boolean matches(int modifiers) {
      return (modifiers & Modifier.ABSTRACT) == 0;
    }
  };

  private final int flag;

  Modifiers(int flag) {
    this.flag = flag;
  }

  @Override
  public boolean test(Class<?> c) {
    return matches(c.getModifiers());
  }

  public Predicate<Member> member() {
    return this::matches;
  }

  public boolean matches(Member member) {
    return matches(member.getModifiers());
  }

  protected boolean matches(int modifiers) {
    return (modifiers & flag) != 0;
  }
}