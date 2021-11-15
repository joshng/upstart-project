package upstart.util.geometry;

import upstart.util.SelfType;

public interface Translatable<T extends Translatable<T>> extends SelfType<T> {
  default T right(int x) {
    return x == 0 ? self() : translate(x, 0);
  }

  default T left(int x) {
    return right(-x);
  }

  default T down(int y) {
    return y == 0 ? self() : translate(0, y);
  }

  default T up(int y) {
    return down(-y);
  }

  T translate(int x, int y);

  default T translate(Direction direction, int distance) {
    return direction.translate(this.self(), distance);
  }

  enum Direction {
    Up {
      @Override
      public <T extends Translatable<T>> T translate(T subject, int distance) {
        return subject.up(distance);
      }
    },
    Down {
      @Override
      public <T extends Translatable<T>> T translate(T subject, int distance) {
        return subject.down(distance);
      }
    },
    Left {
      @Override
      public <T extends Translatable<T>> T translate(T subject, int distance) {
        return subject.left(distance);
      }
    },
    Right {
      @Override
      public <T extends Translatable<T>> T translate(T subject, int distance) {
        return subject.right(distance);
      }
    };

    public abstract <T extends Translatable<T>> T translate(T subject, int distance);
  }
}
