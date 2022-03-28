package upstart.util.functions;

@FunctionalInterface
public interface TriFunction<A, B, C, O> {
  O apply(A a, B b, C c);
}
