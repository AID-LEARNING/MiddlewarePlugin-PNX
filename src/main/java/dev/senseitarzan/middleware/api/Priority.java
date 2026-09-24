package dev.senseitarzan.middleware.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenient alias annotation for {@link AttributeMiddlewarePriority}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Priority {

    /**
     * The priority level of the middleware.
     *
     * @return the middleware priority level
     */
    MiddlewarePriority value() default MiddlewarePriority.NORMAL;
}
