package upstart.web.test;

import upstart.config.EnvironmentConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@EnvironmentConfig.Fixture("upstart.log.levels.\"io.javalin.Javalin\": INFO")
public @interface ShowJavalinLogs {
}
