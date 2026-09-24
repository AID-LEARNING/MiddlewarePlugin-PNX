package dev.senseitarzan.middleware.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicitly configures the execution mode for a middleware class.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Execution {

    /**
     * The configured execution mode.
     *
     * @return the execution mode
     */
    MiddlewareExecutionMode value() default MiddlewareExecutionMode.ON;
}
