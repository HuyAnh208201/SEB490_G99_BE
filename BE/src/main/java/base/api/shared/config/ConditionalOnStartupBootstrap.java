package base.api.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates {@code *Migration} / {@code *Seeder} beans so they only run when
 * {@code app.startup.bootstrap-enabled=true}. Default is false for fast boot
 * against the shared remote DB (schema and catalog already applied).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "app.startup.bootstrap-enabled", havingValue = "true")
public @interface ConditionalOnStartupBootstrap {
}
