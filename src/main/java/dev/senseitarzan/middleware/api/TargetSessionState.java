package dev.senseitarzan.middleware.api;

import org.powernukkitx.network.process.SessionState;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Filters middleware execution to a specific {@link SessionState}
 * (e.g. {@code SessionState.LOGIN}, {@code SessionState.RESOURCE_PACK}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TargetSessionState {

    /**
     * The required SessionState for the middleware to trigger.
     *
     * @return the target session state
     */
    SessionState value();
}
