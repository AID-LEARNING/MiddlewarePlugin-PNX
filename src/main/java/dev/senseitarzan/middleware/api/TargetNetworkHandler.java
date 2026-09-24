package dev.senseitarzan.middleware.api;

import org.powernukkitx.network.process.PacketHandler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation targeting a specific PowerNukkitX {@link PacketHandler} (NetworkHandler).
 * For example: {@code @TargetNetworkHandler(LoginHandler.class)}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TargetNetworkHandler {

    /**
     * The PowerNukkitX PacketHandler class to target.
     *
     * @return the packet handler class
     */
    Class<? extends PacketHandler> value();
}
