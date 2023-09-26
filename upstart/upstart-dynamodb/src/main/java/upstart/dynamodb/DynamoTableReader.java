package upstart.dynamodb;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.NestedAttributeName;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import upstart.aws.FluxPromise;
import upstart.util.concurrent.ListPromise;
import upstart.util.concurrent.OptionalPromise;
import upstart.util.concurrent.Promise;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

public interface DynamoTableReader<S, T> {
  default Flux<S> itemFlux(PagePublisher<S> pagePublisher) {
    return Flux.from(pagePublisher.items());
  }

  Consumer<QueryEnhancedRequest.Builder> queryDecorator();
  ItemExtractor<S, T> extractor();

  DynamoTableDao<S, ?> dao();

  default DynamoDbAsyncTable<S> enhancedTable() {
    return dao().enhancedTable();
  }

  default OptionalPromise<T> getItem(Key key) {
    return OptionalPromise.ofFutureNullable(enhancedTable().getItem(key))
            .thenMapCompose(this::unpack);
  }

  default Flux<T> query(
          Consumer<QueryEnhancedRequest.Builder> requestConsumer,
          NestedAttributeName... extraAttributes
  ) {
    return query(requestConsumer, Arrays.asList(extraAttributes));
  }

  default Flux<T> query(
          Consumer<QueryEnhancedRequest.Builder> requestConsumer,
          Collection<NestedAttributeName> extraAttributes
  ) {
    return toFlux(enhancedTable().query(
            b -> queryDecorator().andThen(requestConsumer).accept(b.addNestedAttributesToProject(extraAttributes))
    ));
  }

  default Flux<T> queryIndex(
          DynamoDbAsyncIndex<S> index,
          Consumer<QueryEnhancedRequest.Builder> requestConsumer,
          NestedAttributeName... extraAttributes
  ) {
    return queryIndex(index, requestConsumer, Arrays.asList(extraAttributes));
  }

  default Flux<T> queryIndex(
          DynamoDbAsyncIndex<S> index,
          Consumer<QueryEnhancedRequest.Builder> requestConsumer,
          Collection<NestedAttributeName> extraAttributes
  ) {
    return toFlux(
            index.query(b -> queryDecorator()
                    .andThen(requestConsumer)
                    .accept(b.addNestedAttributesToProject(extraAttributes)))
    );
  }

  default Flux<T> scan(Consumer<ScanEnhancedRequest.Builder> requestConsumer) {
    return toFlux(enhancedTable().scan(requestConsumer));
  }

  default Promise<T> unpack(S blob) {
    return Promise.of(extractor().extract(blob));
  }

  default <V> ListPromise<V> toList(Flux<V> items) {
    return FluxPromise.toListPromise(items);
  }

  default Flux<T> toFlux(SdkPublisher<Page<S>> pagePublisher) {
    return toFlux(PagePublisher.create(pagePublisher));
  }

  default Flux<T> toFlux(PagePublisher<S> pagePublisher) {
    return itemFlux(pagePublisher)
            .map(this::unpack)
            .flatMap(Mono::fromFuture);
  }

  default <V> DynamoTableReader<S, V> withReadTransformer(ItemTransformer<? super S, ? super T, V> transform) {
    ItemExtractor<S, V> extractorChain = extractor().andThen(transform);
    var reader = dao();
    return new DynamoTableReader<>() {
      @Override
      public Consumer<QueryEnhancedRequest.Builder> queryDecorator() {
        return DynamoTableReader.this.queryDecorator();
      }

      public ItemExtractor<S, V> extractor() {
        return extractorChain;
      }

      @Override
      public DynamoTableDao<S, ?> dao() {
        return reader;
      }
    };
  }

  default DynamoTableReader<S, T> withQueryDecorator(Consumer<QueryEnhancedRequest.Builder> decorator) {
    var reader = dao();
    var decoratorChain = queryDecorator().andThen(decorator);
    return new DynamoTableReader<>() {
      @Override
      public Consumer<QueryEnhancedRequest.Builder> queryDecorator() {
        return decoratorChain;
      }

      public ItemExtractor<S, T> extractor() {
        return DynamoTableReader.this.extractor();
      }

      @Override
      public DynamoTableDao<S, ?> dao() {
        return reader;
      }
    };
  }
}
