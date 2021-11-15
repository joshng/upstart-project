package upstart.proxy;

import com.google.inject.BindingAnnotation;
import com.google.inject.Module;
import com.google.inject.Provides;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The companion-annotation to {@link Lazy}: dependencies which are injected with the {@link Lazy @Lazy} annotation
 * are lazily resolved via a binding of the same type with the {@link LazyProvider} annotation instead. May be
 * most conveniently applied to a {@link Provides}-annotated method in a {@link Module} (see {@link DynamicProxyBindingBuilder#bindLazyProviderProxy}
 * for an example).
 * @see DynamicProxyBindingBuilder
 * @see DynamicProxyBindingBuilder#bindLazyProviderProxy
 * @see Lazy
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@BindingAnnotation
public @interface LazyProvider {}
