package upstart.dynamodb.heterogeneous;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.enhanced.dynamodb.internal.AttributeValues;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import upstart.aws.test.dynamodb.LocalDynamoDbTest;
import upstart.config.UpstartModule;
import upstart.dynamodb.DynamoDbNamespace;
import upstart.dynamodb.DynamoTable;
import upstart.dynamodb.DynamoTableDao;
import upstart.dynamodb.ItemExtractor;
import upstart.test.UpstartLibraryServiceTest;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;

@LocalDynamoDbTest
@UpstartLibraryServiceTest
class MixedDynamoTableReaderTest extends UpstartModule {
  public static final String TABLE_NAME = "shapes";
  @Inject SingleTableReader reader;
  @Inject CircleDao circleDao;
  @Inject SquareDao squareDao;

  @Override
  protected void configure() {
    install(new DynamoTable.TableModule(TABLE_NAME, new DynamoDbNamespace("test-db")));
  }

  @Test
  void roundtrip() {
    Circle redCircle = new Circle(1.0, Color.RED);
    Square redSquare = new Square(2.0, Color.RED);
    Square greenSquare = new Square(2.0, Color.GREEN);
    Promise.allOf(
            circleDao.write(redCircle),
            squareDao.write(redSquare),
            squareDao.write(greenSquare)
    ).join();

    List<Shape> results = reader.allByColor(Color.RED).collectList().block();

    assertThat(results).containsExactly(redCircle, redSquare).inOrder();
  }

  enum Color {
    RED, GREEN, BLUE
  }

  interface Shape {
    Color color();
    double area();
  }

  record Circle(double radius, Color color) implements Shape {
    @Override
    public double area() {
      return Math.PI * radius * radius;
    }
  }

  record Square(double side, Color color) implements Shape {
    @Override
    public double area() {
      return side * side;
    }
  }

  public abstract static class BaseShapeBlob<S extends Shape> extends MixedDynamoTableReader.SortKeyTypePrefixItem {
    private String partitionKey;
    private String sortKey;

    public BaseShapeBlob(S shape) {
      partitionKey = shape.color().toString();
      sortKey = "%06f".formatted(shape.area());
    }

    protected abstract S buildShape();

    public S toShape() {
      S s = buildShape();
      double storedArea = Double.parseDouble(strippedSortKeySuffix());
      assertThat(s.area()).isWithin(0.00001).of(storedArea);
      return s;
    }

    @Deprecated
    public BaseShapeBlob() {
    }


    @Override
    public String partitionKey() {
      return partitionKey;
    }

    @Override
    protected String sortKeySuffix() {
      return sortKey;
    }
  }

  @DynamoTypeId("circle")
  @DynamoDbBean
  public static class CircleBlob extends BaseShapeBlob<Circle> {
    private double radius;

    public CircleBlob(Circle shape) {
      super(shape);
      this.radius = shape.radius();
    }

    public CircleBlob() {
    }

    @Override
    public Circle buildShape() {
      return new Circle(radius, Color.valueOf(getPartitionKey()));
    }

    public double getRadius() {
      return radius;
    }

    public void setRadius(double radius) {
      this.radius = radius;
    }
  }

  @DynamoTypeId("square")
  @DynamoDbBean
  public static class SquareBlob extends BaseShapeBlob<Square> {
    private double side;

    public SquareBlob(Square shape) {
      super(shape);
      this.side = shape.side();
    }

    public SquareBlob() {
    }

    public double getSide() {
      return side;
    }

    public void setSide(double side) {
      this.side = side;
    }

    @Override
    public Square buildShape() {
      return new Square(side, Color.valueOf(getPartitionKey()));
    }
  }

  @DynamoTypeId("circle")
  @Singleton
  static class CircleDao extends DynamoTableDao<CircleBlob, Circle> {
    @Inject
    public CircleDao(@Named(TABLE_NAME) DynamoTable table) {
      super(CircleBlob.class, table, ItemExtractor.of(CircleBlob::toShape));
    }

    Promise<Void> write(Circle circle) {
      return Promise.of(enhancedTable.putItem(b -> b.item(new CircleBlob(circle))));
    }
  }

  @DynamoTypeId("square")
  @Singleton
  static class SquareDao extends DynamoTableDao<SquareBlob, Square> {
    @Inject
    public SquareDao(@Named(TABLE_NAME) DynamoTable table) {
      super(SquareBlob.class, table, ItemExtractor.of(SquareBlob::toShape));
    }

    Promise<Void> write(Square square) {
      return Promise.of(enhancedTable.putItem(b -> b.item(new SquareBlob(square))));
    }
  }

  static class SingleTableReader extends MixedDynamoTableReader<BaseShapeBlob<?>, Shape> {
    @Inject
    private SingleTableReader(
            CircleDao circleReader,
            SquareDao squareReader,
            @Named(TABLE_NAME) DynamoTable table
    ) {
      super(new SortKeyTypePrefixExtractor(), table, List.of(circleReader, squareReader));
    }

    public Flux<Shape> allByColor(Color color) {
      return query(b -> b
              .keyConditionExpression("#pk = :pk")
              .expressionAttributeNames(Map.of("#pk", BaseShapeBlob.PARTITION_KEY_ATTRIBUTE))
              .expressionAttributeValues(Map.of(":pk", AttributeValues.stringValue(color.toString())))
      );
    }
  }

//  static class CircleWriter extends DynamoTableWriter<CircleBlob, PackedRecord> {
//    @Inject
//    public CircleWriter(AvroDynamoWriteSupport<PackedRecord, CircleBlob> writeSupport) {
//      super(writeSupport);
//    }
//  }
//
//  static class SquareWriter extends AvroDynamoTableWriter<SquareBlob, PackedRecord> {
//    @Inject
//    public SquareWriter(AvroDynamoWriteSupport<PackedRecord, SquareBlob> writeSupport) {
//      super(writeSupport);
//    }
//  }
}