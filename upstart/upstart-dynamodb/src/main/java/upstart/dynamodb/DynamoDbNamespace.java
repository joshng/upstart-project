package upstart.dynamodb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.inject.Binder;
import upstart.config.UpstartConfigBinder;
import upstart.config.UpstartContext;

import java.util.Objects;

public class DynamoDbNamespace {
  private final String namespace;

  public static DynamoDbNamespace environmentNamespace(Binder binder) {
    return environmentNamespace(UpstartConfigBinder.get().bindConfig(binder, UpstartContext.class));
  }

  public static DynamoDbNamespace environmentNamespace(UpstartContext upstartContext) {
    return new DynamoDbNamespace(upstartContext.environment());
  }

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
