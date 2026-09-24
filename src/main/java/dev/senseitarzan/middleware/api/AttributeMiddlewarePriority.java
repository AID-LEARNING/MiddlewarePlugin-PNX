package dev.senseitarzan.middleware.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation defining the execution priority of a middleware class.
 * Corresponds to PocketMine-MP's #[AttributeMiddlewarePriority] attribute.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AttributeMiddlewarePriority {

    /**
     * The priority level of the middleware.
     *
     * @return the middleware priority level
     */
    MiddlewarePriority value() default MiddlewarePriority.NORMAL;
}
