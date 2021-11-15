package upstart;

public enum UpstartDeploymentStage {
  test,
  dev,
  stage,
  prod;

  public boolean isDevelopmentMode() {
    return this == dev;
  }

  public boolean isProductionLike() {
    return ordinal() > dev.ordinal();
  }

}
