package upstart.util;

public interface SelfType<S extends SelfType<S>> {
  @SuppressWarnings("unchecked")
  default S self() {
    return (S) this;
  }
}
