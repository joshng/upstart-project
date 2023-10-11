package upstart.dynamodb.heterogeneous;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Converter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.internal.AttributeValues;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
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
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
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
    Circle redCircle = new Circle(1, Color.RED);
    Square redSquare = new Square(2, Color.RED);
    Square greenSquare = new Square(2, Color.GREEN);
    Promise.allOf(
            circleDao.write(redCircle),
            squareDao.write(redSquare),
            squareDao.write(greenSquare)
    ).join();

    List<Shape> redShapes = reader.allByColor(Color.RED).collectList().block();
    assertThat(redShapes).containsExactly(redCircle, redSquare).inOrder();

    List<Square> sideResults = squareDao.queryBySide(2).collectList().block();
    assertThat(sideResults).containsExactly(greenSquare, redSquare).inOrder();

    List<Shape> squareResults = reader.queryByVertices(4).collectList().block();
    assertThat(squareResults).containsExactly(redSquare, greenSquare);
  }

  @Test
  void provisionsIndex() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode obj = objectMapper.convertValue(
            circleDao.table().resourceRequirement().resourceConfig(),
            ObjectNode.class
    );

    List<String> globalIndexNames = obj.get("globalSecondaryIndexes").findValuesAsText("indexName");
    assertThat(globalIndexNames).containsExactly(SingleTableReader.VERTEX_INDEX, SquareDao.SIDE_INDEX);
//    System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj));
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

  record Square(int side, Color color) implements Shape {
    @Override
    public double area() {
      return side * side;
    }
  }

  @DynamoDbBean
  public abstract static class BaseShapeBean<S extends Shape> extends MixedTableDynamoBean.Base implements SortKeyTypePrefixBean.Converted<Double> {
    private String partitionKey;
    private Double area;

    public BaseShapeBean(S shape) {
      partitionKey = shape.color().toString();
      area = shape.area();
    }

    @Deprecated
    public BaseShapeBean() {
    }

    protected abstract S buildShape();

    public S toShape() {
      S s = buildShape();
      double storedArea = Double.parseDouble(strippedSortKeySuffix());
      assertThat(s.area()).isWithin(0.00001).of(storedArea);
      return s;
    }

    @Override
    public Double sortValue() {
      return area;
    }

    @Override
    public void setSortValue(Double sortValue) {
      area = sortValue;
    }

    @Override
    public Converter<Double, String> sortValueConverter() {
      return Converter.from("%04f"::formatted, Double::parseDouble);
    }

    @DynamoDbSecondaryPartitionKey(indexNames = SingleTableReader.VERTEX_INDEX)
    public int getVertices() {
      return vertices();
    }

    protected abstract int vertices();

    public void setVertices(int v) {
      checkArgument(v == vertices(), "Mismatched vertices");
    }


    @Override
    public String partitionKey() {
      return partitionKey;
    }
  }

  @DynamoTypeId("circle")
  @DynamoDbBean
  public static class CircleBean extends BaseShapeBean<Circle> {
    private double radius;

    public CircleBean(Circle shape) {
      super(shape);
      this.radius = shape.radius();
    }

    public CircleBean() {
    }

    @Override
    public Circle buildShape() {
      return new Circle(radius, Color.valueOf(getPartitionKey()));
    }

    @Override
    public int vertices() {
      return Integer.MAX_VALUE;
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
  public static class SquareBean extends BaseShapeBean<Square> {
    private int side;

    public SquareBean(Square shape) {
      super(shape);
      this.side = shape.side();
    }

    public SquareBean() {
    }

    @DynamoDbPartitionKey
    @DynamoDbSecondarySortKey(indexNames = SquareDao.SIDE_INDEX)
    @DynamoDbAttribute(PARTITION_KEY_ATTRIBUTE)
    @Override
    public String getPartitionKey() {
      return super.getPartitionKey();
    }

    @Override
    public int vertices() {
      return 4;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = SquareDao.SIDE_INDEX)
    public int getSide() {
      return side;
    }

    public void setSide(int side) {
      this.side = side;
    }

    @Override
    public Square buildShape() {
      return new Square(side, Color.valueOf(getPartitionKey()));
    }
  }

  @DynamoTypeId("circle")
  @Singleton
  static class CircleDao extends DynamoTableDao<CircleBean, Circle> {
    @Inject
    public CircleDao(@Named(TABLE_NAME) DynamoTable table) {
      super(CircleBean.class, table, ItemExtractor.of(CircleBean::toShape));
    }

    Promise<Void> write(Circle circle) {
      return Promise.of(enhancedTable.putItem(b -> b.item(new CircleBean(circle))));
    }
  }

  @DynamoTypeId("square")
  @Singleton
  static class SquareDao extends DynamoTableDao<SquareBean, Square> {
    public static final String SIDE_INDEX = "side-index";
    private DynamoDbAsyncIndex<SquareBean> sideIndex;

    @Inject
    public SquareDao(@Named(TABLE_NAME) DynamoTable table) {
      super(SquareBean.class, table, ItemExtractor.of(SquareBean::toShape));
      sideIndex = enhancedTable.index(SIDE_INDEX);
    }

    Promise<Void> write(Square square) {
      return Promise.of(enhancedTable.putItem(b -> b.item(new SquareBean(square))));
    }

    Flux<Square> queryBySide(int side) {
      return queryIndex(sideIndex, b -> b.queryConditional(QueryConditional.keyEqualTo(kb -> kb.partitionValue(side))));
    }


    @Override
    public CreateTableEnhancedRequest.Builder prepareCreateTableRequest(CreateTableEnhancedRequest.Builder builder) {
      return builder.globalSecondaryIndices(
              b -> b.indexName(SIDE_INDEX).projection(proj -> proj.projectionType(ProjectionType.ALL))
      );
    }
  }

  static class SingleTableReader extends MixedDynamoTableReader<BaseShapeBean<?>, Shape> {
    public static final String VERTEX_INDEX = "vertex-index";

    @Inject
    private SingleTableReader(
            CircleDao circleReader,
            SquareDao squareReader,
            @Named(TABLE_NAME) DynamoTable table
    ) {
      super(BaseShapeBean.class, new SortKeyTypePrefixExtractor(), table, Set.of(circleReader, squareReader));
    }

    public Flux<Shape> allByColor(Color color) {
      return query(b -> b
              .keyConditionExpression("#pk = :pk")
              .expressionAttributeNames(Map.of("#pk", BaseShapeBean.PARTITION_KEY_ATTRIBUTE))
              .expressionAttributeValues(Map.of(":pk", AttributeValues.stringValue(color.toString())))
      );
    }

    public Flux<Shape> queryByVertices(int vertices) {
      return query(b -> b.indexName(VERTEX_INDEX)
              .keyConditionExpression("#v = :v")
              .expressionAttributeNames(Map.of("#v", "vertices"))
              .expressionAttributeValues(Map.of(":v", AttributeValues.numberValue(vertices)))
      );
    }

    @Override
    public CreateTableEnhancedRequest.Builder prepareCreateTableRequest(CreateTableEnhancedRequest.Builder builder) {
      return builder.globalSecondaryIndices(b -> b.indexName(VERTEX_INDEX).projection(pb -> pb.projectionType(ProjectionType.ALL)));
    }
  }
}