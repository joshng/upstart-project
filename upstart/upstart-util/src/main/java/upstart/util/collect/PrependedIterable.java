package upstart.util.collect;

import com.google.common.collect.Iterators;

import java.util.Iterator;
import java.util.List;

public class PrependedIterable<T> implements Iterable<T> {
  private final T first;
  private final Iterable<T> rest;

  public PrependedIterable(T first, Iterable<T> rest) {
    this.first = first;
    this.rest = rest;
  }

  @Override
  public Iterator<T> iterator() {
    Iterator<T> restIterator = rest.iterator();
    if (!restIterator.hasNext()) return Iterators.singletonIterator(first);
    return new Iterator<>() {
      long idx = 0;

      @Override
      public boolean hasNext() {
        return idx < 2 || restIterator.hasNext();
      }

      @Override
      public T next() {
        if (idx++ == 0) {
          return first;
        } else {
          return restIterator.next();
        }
      }
    };
  }
}
