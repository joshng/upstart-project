package upstart.cluster;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;

public class ConsistentHashRing<Worker> {
  private final HashFunction hashFunction;
  private final NavigableMap<Long, Worker> hashRing = new TreeMap<>();
  private final Multimap<Worker, Long> workerNodes = ArrayListMultimap.create();
  private final int nodesPerWorker;
  private final ToLongFunction<? super Worker> workerHasher;
  private volatile Map.Entry<Long, Worker> headEntry = null;

  public ConsistentHashRing(int nodesPerWorker, ToLongFunction<? super Worker> workerHasher, HashFunction hashFunction) {
    this.nodesPerWorker = nodesPerWorker;
    this.workerHasher = workerHasher;
    this.hashFunction = hashFunction;
  }

  public ConsistentHashRing(int nodesPerWorker, BiConsumer<Worker, Hasher> workerHasher) {
    this(nodesPerWorker, worker -> computeHash(worker, Hashing.sha256(), workerHasher), Hashing.sha256());
  }

  public void addWorkers(Stream<Worker> workers) {
    workers.filter(worker -> !workerNodes.containsKey(worker)).forEach(worker -> {
      long hash = workerHasher.applyAsLong(worker);
      for (int i = 0; i < nodesPerWorker; i++) {
        Worker collision = hashRing.put(hash, worker);
        checkState(collision == null, "Hash collision?! Can't proceed, this could result in non-deterministic results", collision, worker);
        workerNodes.put(worker, hash);
        hash = hashFunction.hashLong(hash).padToLong();
      }
    });
    finishUpdate();
  }

  public void removeWorkers(Stream<Worker> workers) {
    workers.forEach(worker -> {
      for (Long node : workerNodes.removeAll(worker)) {
        hashRing.remove(node, worker);
      }
    });
    finishUpdate();
  }

  private void finishUpdate() {
    headEntry = hashRing.firstEntry();
  }

  private static <T> long computeHash(T value, HashFunction hashFunction, BiConsumer<T, Hasher> hashPopulator) {
    Hasher hasher = hashFunction.newHasher();
    hashPopulator.accept(value, hasher);
    return hasher.hash().padToLong();
  }

  public Worker assignWorker(long assignment) {
    return MoreObjects.firstNonNull(hashRing.higherEntry(assignment), headEntry).getValue();
  }

  public <T> Set<T> computeAssignments(Worker worker, Collection<T> items, ToLongFunction<? super T> hashComputer) {
    if (hashRing.isEmpty()) return ImmutableSet.of();
    return items.stream()
            .filter(item -> assignWorker(hashComputer.applyAsLong(item)).equals(worker))
            .collect(Collectors.toSet());
  }

}
