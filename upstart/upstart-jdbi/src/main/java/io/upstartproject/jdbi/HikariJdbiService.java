package io.upstartproject.jdbi;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.spi.JdbiPlugin;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class HikariJdbiService extends AbstractJdbiService {
  private final HikariConfig config;

  public HikariJdbiService(HikariConfig config, Set<JdbiPlugin> plugins) {
    super(plugins);
    config.validate();
    this.config = config;
  }

  @Override
  protected Jdbi buildJdbi() {
    return Jdbi.create(new HikariDataSource(config));
  }
}
