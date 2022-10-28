package upstart.util.functions;

@FunctionalInterface
public interface QuadFunction<A, B, C, D, O> {
  O apply(A a, B b, C c, D d);
}
