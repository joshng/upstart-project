package upstart.dynamodb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import javax.inject.Inject;
import java.util.Objects;

public class DynamoDbNamespace {
  private final String namespace;

  @JsonCreator
  public DynamoDbNamespace(String namespace) {
    this.namespace = Objects.requireNonNull(namespace, "namespace");
  }

  public String tableName(String suffix) {
    return namespace + '.' + suffix;
  }

  @JsonValue
  public String namespace() {
    return namespace;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof DynamoDbNamespace ns && namespace.equals(ns.namespace);
  }

  @Override
  public int hashCode() {
    return namespace.hashCode();
  }
}
