package dev.senseitarzan.middleware.api;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation targeting multiple Bedrock packet classes.
 * For example: {@code @TargetPackets({InteractPacket.class, PlayerActionPacket.class})}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TargetPackets {

    /**
     * An array of Bedrock packet classes to intercept.
     *
     * @return array of packet classes
     */
    Class<? extends BedrockPacket>[] value();
}
