package upstart.proxy;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the associated parameter will be injected as a <strong>Proxy</strong> object, which is resolved
 * dynamically to obtain the real implementation when first invoked.
 *
 * @see DynamicProxyBindingBuilder
 * @see DynamicProxyBindingBuilder#bindLazyProviderProxy
 * @see LazyProvider
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@BindingAnnotation
public @interface Lazy {}
