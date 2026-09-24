package dev.senseitarzan.middleware.api;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation targeting a specific Bedrock packet class.
 * For example: {@code @TargetPacket(CommandRequestPacket.class)}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TargetPacket {

    /**
     * The Bedrock packet class to intercept.
     *
     * @return the packet class
     */
    Class<? extends BedrockPacket> value();
}
