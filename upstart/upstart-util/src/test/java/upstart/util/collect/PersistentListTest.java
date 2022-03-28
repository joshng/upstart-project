package upstart.util.collect;

import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

class PersistentListTest {
  @Test
  void withPrependsElements() {
    PersistentList<Integer> list = PersistentList.of(1).with(2).with(3);
    assertThat(list).containsExactly(3, 2, 1).inOrder();
  }

  @Test
  void builderAppendsElements() {
    PersistentList<Integer> list = PersistentList.<Integer>builder().add(1).add(2).build();
    assertThat(list).containsExactly(1, 2).inOrder();
  }

  @Test
  void withoutPerformsImmutableRemoval() {
    PersistentList<Integer> list = PersistentList.of(2).with(1);
    PersistentList<Integer> removed = list.without(2);

    assertThat(removed).containsExactly(1);

    assertThat(list).containsExactly(1, 2).inOrder();

  }
}