package dev.senseitarzan.middleware;

import dev.senseitarzan.middleware.api.*;
import dev.senseitarzan.middleware.hook.MiddlewarePacketHandler;
import dev.senseitarzan.middleware.hook.PacketHandlerHook;
import dev.senseitarzan.middleware.manager.MiddlewareManager;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powernukkitx.config.category.network.RateLimitSettings;
import org.powernukkitx.network.process.PacketHandler;
import org.powernukkitx.network.process.PacketHandlerRegistry;
import org.powernukkitx.network.process.PlayerSessionHolder;
import org.powernukkitx.network.process.handler.LoginHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class MiddlewareTest {

    private MiddlewareManager manager;

    @BeforeEach
    public void setUp() {
        manager = MiddlewareManager.getInstance();
        manager.clear();
        PacketHandlerHook.unhookAll();
    }

    @Test
    public void testAutomaticPacketClassInferenceFromTemplate() {
        class AutoInferredMiddleware implements IMiddleware<AnimatePacket> {
            @Override
            public String getName() {
                return "AutoInferred";
            }

            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<AnimatePacket> context) {
                return CompletableFuture.completedFuture(null);
            }
        }

        AutoInferredMiddleware middleware = new AutoInferredMiddleware();
        assertEquals(AnimatePacket.class, middleware.getPacketClass(),
                "getPacketClass() should automatically resolve to AnimatePacket.class from the generic template argument");

        manager.addMiddleware(middleware);

        assertTrue(PacketHandlerRegistry.getPacketHandler(AnimatePacket.class) instanceof MiddlewarePacketHandler<?>);
    }

    @Test
    public void testPriorityOrdering() {
        List<String> executionOrder = new ArrayList<>();

        @Priority(MiddlewarePriority.HIGHEST)
        class HighestMiddleware implements IMiddleware<LoginPacket> {
            @Override
            public String getName() { return "Highest"; }
            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<LoginPacket> context) {
                executionOrder.add("HIGHEST");
                return CompletableFuture.completedFuture(null);
            }
        }

        @Priority(MiddlewarePriority.LOWEST)
        class LowestMiddleware implements IMiddleware<LoginPacket> {
            @Override
            public String getName() { return "Lowest"; }
            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<LoginPacket> context) {
                executionOrder.add("LOWEST");
                return CompletableFuture.completedFuture(null);
            }
        }

        class NormalMiddleware implements IMiddleware<LoginPacket> {
            @Override
            public String getName() { return "Normal"; }
            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<LoginPacket> context) {
                executionOrder.add("NORMAL");
                return CompletableFuture.completedFuture(null);
            }
        }

        manager.addMiddleware(new HighestMiddleware());
        manager.addMiddleware(new LowestMiddleware());
        manager.addMiddleware(new NormalMiddleware());

        LoginPacket loginPacket = new LoginPacket();
        MiddlewareContext<LoginPacket> context = new MiddlewareContext<>(loginPacket, null, null);

        manager.executePipeline(context).join();

        assertEquals(List.of("LOWEST", "NORMAL", "HIGHEST"), executionOrder);
    }

    @Test
    public void testOnceVsOnExecutionModes() {
        AtomicInteger onceCount = new AtomicInteger();
        AtomicInteger onCount = new AtomicInteger();

        @Once
        class OnceMiddleware implements IMiddleware<CommandRequestPacket> {
            @Override
            public String getName() { return "OnceMiddleware"; }
            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<CommandRequestPacket> context) {
                onceCount.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        }

        @On
        class OnMiddleware implements IMiddleware<CommandRequestPacket> {
            @Override
            public String getName() { return "OnMiddleware"; }
            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<CommandRequestPacket> context) {
                onCount.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        }

        manager.addMiddleware(new OnceMiddleware());
        manager.addMiddleware(new OnMiddleware());

        RateLimitSettings rateLimits = new RateLimitSettings();
        PlayerSessionHolder session = new PlayerSessionHolder(null, rateLimits);

        CommandRequestPacket packet1 = new CommandRequestPacket();
        CommandRequestPacket packet2 = new CommandRequestPacket();
        CommandRequestPacket packet3 = new CommandRequestPacket();

        manager.executePipeline(new MiddlewareContext<>(packet1, session, null)).join();
        assertEquals(1, onceCount.get(), "OnceMiddleware should have run on packet 1");
        assertEquals(1, onCount.get(), "OnMiddleware should have run on packet 1");

        manager.executePipeline(new MiddlewareContext<>(packet2, session, null)).join();
        assertEquals(1, onceCount.get(), "OnceMiddleware should NOT run a 2nd time in same session");
        assertEquals(2, onCount.get(), "OnMiddleware should run a 2nd time");

        manager.executePipeline(new MiddlewareContext<>(packet3, session, null)).join();
        assertEquals(1, onceCount.get(), "OnceMiddleware should NOT run a 3rd time in same session");
        assertEquals(3, onCount.get(), "OnMiddleware should run a 3rd time");
    }

    @Test
    public void testDynamicRegisterOnceAndRegisterOn() {
        AtomicInteger onceCount = new AtomicInteger();
        AtomicInteger onCount = new AtomicInteger();

        class PlainMiddleware implements IMiddleware<InteractPacket> {
            private final AtomicInteger counter;
            private final String name;

            public PlainMiddleware(String name, AtomicInteger counter) {
                this.name = name;
                this.counter = counter;
            }

            @Override
            public String getName() { return name; }
            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<InteractPacket> context) {
                counter.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        }

        manager.registerOnce(new PlainMiddleware("DynamicOnce", onceCount));
        manager.registerOn(new PlainMiddleware("DynamicOn", onCount));

        RateLimitSettings rateLimits = new RateLimitSettings();
        PlayerSessionHolder session = new PlayerSessionHolder(null, rateLimits);

        InteractPacket p1 = new InteractPacket();
        InteractPacket p2 = new InteractPacket();

        manager.executePipeline(new MiddlewareContext<>(p1, session, null)).join();
        assertEquals(1, onceCount.get());
        assertEquals(1, onCount.get());

        manager.executePipeline(new MiddlewareContext<>(p2, session, null)).join();
        assertEquals(1, onceCount.get());
        assertEquals(2, onCount.get());
    }

    @Test
    public void testTargetAnyArbitraryPacketOnlyHooksTargetPacket() {
        class CommandMiddleware implements IMiddleware<CommandRequestPacket> {
            @Override
            public String getName() { return "CommandMiddleware"; }
            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<CommandRequestPacket> context) {
                return CompletableFuture.completedFuture(null);
            }
        }

        manager.addMiddleware(new CommandMiddleware());

        PacketHandler commandHandler = PacketHandlerRegistry.getPacketHandler(CommandRequestPacket.class);
        assertTrue(commandHandler instanceof MiddlewarePacketHandler<?>);

        PacketHandler textHandler = PacketHandlerRegistry.getPacketHandler(TextPacket.class);
        assertFalse(textHandler instanceof MiddlewarePacketHandler<?>);

        PacketHandler loginHandler = PacketHandlerRegistry.getPacketHandler(LoginPacket.class);
        assertFalse(loginHandler instanceof MiddlewarePacketHandler<?>);
    }

    @Test
    public void testTargetMultiplePackets() {
        List<String> received = new ArrayList<>();

        @TargetPackets({InteractPacket.class, PlayerActionPacket.class})
        class MultiPacketMiddleware implements IMiddleware<BedrockPacket> {
            @Override
            public String getName() { return "MultiPacket"; }
            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<BedrockPacket> context) {
                received.add(context.getPacket().getClass().getSimpleName());
                return CompletableFuture.completedFuture(null);
            }
        }

        manager.addMiddleware(new MultiPacketMiddleware());

        assertTrue(PacketHandlerRegistry.getPacketHandler(InteractPacket.class) instanceof MiddlewarePacketHandler<?>);
        assertTrue(PacketHandlerRegistry.getPacketHandler(PlayerActionPacket.class) instanceof MiddlewarePacketHandler<?>);
        assertFalse(PacketHandlerRegistry.getPacketHandler(TextPacket.class) instanceof MiddlewarePacketHandler<?>);

        InteractPacket interact = new InteractPacket();
        PlayerActionPacket action = new PlayerActionPacket();

        manager.executePipeline(new MiddlewareContext<>(interact, null, null)).join();
        manager.executePipeline(new MiddlewareContext<>(action, null, null)).join();

        assertEquals(List.of("InteractPacket", "PlayerActionPacket"), received);
    }

    @Test
    public void testTargetNetworkHandlerHooksTargetedPacketOnly() {
        List<String> executed = new ArrayList<>();

        @TargetNetworkHandler(LoginHandler.class)
        class LoginHandlerMiddleware implements IMiddleware<BedrockPacket> {
            @Override
            public String getName() { return "LoginHandlerOnly"; }
            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<BedrockPacket> context) {
                executed.add("LOGIN_EXECUTED");
                return CompletableFuture.completedFuture(null);
            }
        }

        manager.addMiddleware(new LoginHandlerMiddleware());

        assertTrue(PacketHandlerRegistry.getPacketHandler(LoginPacket.class) instanceof MiddlewarePacketHandler<?>);
        assertFalse(PacketHandlerRegistry.getPacketHandler(TextPacket.class) instanceof MiddlewarePacketHandler<?>);

        LoginPacket packet = new LoginPacket();
        LoginHandler loginHandler = new LoginHandler();

        MiddlewareContext<LoginPacket> matching = new MiddlewareContext<>(packet, null, null, loginHandler);
        manager.executePipeline(matching).join();
        assertEquals(List.of("LOGIN_EXECUTED"), executed);
    }

    @Test
    public void testSessionSpecificMiddlewareOnlyExecutesForTargetSession() {
        RateLimitSettings rateLimits = new RateLimitSettings();
        PlayerSessionHolder sessionHolder1 = new PlayerSessionHolder(null, rateLimits);
        PlayerSessionHolder sessionHolder2 = new PlayerSessionHolder(null, rateLimits);

        NetworkSession session1 = NetworkSession.from(sessionHolder1);
        NetworkSession session2 = NetworkSession.from(sessionHolder2);

        List<String> session1Calls = new ArrayList<>();
        List<String> session2Calls = new ArrayList<>();

        class Session1OnlyMiddleware implements IMiddleware<TextPacket> {
            @Override
            public String getName() { return "Session1Only"; }
            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<TextPacket> context) {
                session1Calls.add("S1_PACKET");
                return CompletableFuture.completedFuture(null);
            }
        }

        session1.addMiddleware(new Session1OnlyMiddleware());

        TextPacket packet = new TextPacket();
        manager.executePipeline(new MiddlewareContext<>(packet, sessionHolder1, null)).join();

        assertEquals(List.of("S1_PACKET"), session1Calls, "Session 1 middleware should have executed");
        assertTrue(session2Calls.isEmpty());

        manager.executePipeline(new MiddlewareContext<>(packet, sessionHolder2, null)).join();

        assertEquals(List.of("S1_PACKET"), session1Calls, "Session 1 calls should not increase");
        assertTrue(session2Calls.isEmpty(), "Session 2 should NOT execute Session 1 middleware");
    }

    @Test
    public void testGlobalAndSessionMiddlewaresPriorityOrdering() {
        RateLimitSettings rateLimits = new RateLimitSettings();
        PlayerSessionHolder sessionHolder1 = new PlayerSessionHolder(null, rateLimits);
        PlayerSessionHolder sessionHolder2 = new PlayerSessionHolder(null, rateLimits);

        NetworkSession session1 = NetworkSession.from(sessionHolder1);

        List<String> executionOrder = new ArrayList<>();

        @AttributeMiddlewarePriority(MiddlewarePriority.HIGHEST)
        class GlobalHighest implements IMiddleware<TextPacket> {
            @Override public String getName() { return "GlobalHighest"; }
            @Override public CompletableFuture<Void> handle(MiddlewareContext<TextPacket> context) {
                executionOrder.add("GLOBAL_HIGHEST");
                return CompletableFuture.completedFuture(null);
            }
        }

        @AttributeMiddlewarePriority(MiddlewarePriority.LOWEST)
        class GlobalLowest implements IMiddleware<TextPacket> {
            @Override public String getName() { return "GlobalLowest"; }
            @Override public CompletableFuture<Void> handle(MiddlewareContext<TextPacket> context) {
                executionOrder.add("GLOBAL_LOWEST");
                return CompletableFuture.completedFuture(null);
            }
        }

        @AttributeMiddlewarePriority(MiddlewarePriority.NORMAL)
        class SessionNormal implements IMiddleware<TextPacket> {
            @Override public String getName() { return "SessionNormal"; }
            @Override public CompletableFuture<Void> handle(MiddlewareContext<TextPacket> context) {
                executionOrder.add("SESSION_NORMAL");
                return CompletableFuture.completedFuture(null);
            }
        }

        @AttributeMiddlewarePriority(MiddlewarePriority.LOW)
        class SessionLow implements IMiddleware<TextPacket> {
            @Override public String getName() { return "SessionLow"; }
            @Override public CompletableFuture<Void> handle(MiddlewareContext<TextPacket> context) {
                executionOrder.add("SESSION_LOW");
                return CompletableFuture.completedFuture(null);
            }
        }

        manager.addMiddleware(new GlobalHighest());
        manager.addMiddleware(new GlobalLowest());

        session1.addMiddleware(new SessionNormal());
        session1.addMiddleware(new SessionLow());

        TextPacket packet = new TextPacket();

        manager.executePipeline(new MiddlewareContext<>(packet, sessionHolder1, null)).join();
        assertEquals(List.of("GLOBAL_LOWEST", "SESSION_LOW", "SESSION_NORMAL", "GLOBAL_HIGHEST"), executionOrder);

        executionOrder.clear();
        manager.executePipeline(new MiddlewareContext<>(packet, sessionHolder2, null)).join();
        assertEquals(List.of("GLOBAL_LOWEST", "GLOBAL_HIGHEST"), executionOrder);
    }

    @Test
    public void testSessionRegisterOnceAndRegisterOn() {
        RateLimitSettings rateLimits = new RateLimitSettings();
        PlayerSessionHolder sessionHolder = new PlayerSessionHolder(null, rateLimits);
        NetworkSession session = NetworkSession.from(sessionHolder);

        AtomicInteger onceCount = new AtomicInteger();
        AtomicInteger onCount = new AtomicInteger();

        class SimpleMiddleware implements IMiddleware<TextPacket> {
            private final String name;
            private final AtomicInteger counter;
            SimpleMiddleware(String name, AtomicInteger counter) {
                this.name = name;
                this.counter = counter;
            }
            @Override public String getName() { return name; }
            @Override public CompletableFuture<Void> handle(MiddlewareContext<TextPacket> context) {
                counter.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        }

        session.registerOnce(new SimpleMiddleware("OnceMiddleware", onceCount));
        session.registerOn(new SimpleMiddleware("OnMiddleware", onCount));

        TextPacket packet = new TextPacket();

        manager.executePipeline(new MiddlewareContext<>(packet, sessionHolder, null)).join();
        assertEquals(1, onceCount.get());
        assertEquals(1, onCount.get());

        manager.executePipeline(new MiddlewareContext<>(packet, sessionHolder, null)).join();
        assertEquals(1, onceCount.get(), "ONCE middleware should not run a second time for this session");
        assertEquals(2, onCount.get(), "ON middleware should run on every packet for this session");
    }

    @Test
    public void testContextDynamicSessionMiddlewareAttachment() {
        RateLimitSettings rateLimits = new RateLimitSettings();
        PlayerSessionHolder sessionHolder = new PlayerSessionHolder(null, rateLimits);

        List<String> log = new ArrayList<>();

        class LoginHookMiddleware implements IMiddleware<LoginPacket> {
            @Override public String getName() { return "LoginHook"; }
            @Override public CompletableFuture<Void> handle(MiddlewareContext<LoginPacket> context) {
                log.add("LOGIN");
                context.addSessionMiddleware(new IMiddleware<CommandRequestPacket>() {
                    @Override public String getName() { return "DynamicCommand"; }
                    @Override public CompletableFuture<Void> handle(MiddlewareContext<CommandRequestPacket> ctx) {
                        log.add("COMMAND_DYNAMIC");
                        return CompletableFuture.completedFuture(null);
                    }
                });
                return CompletableFuture.completedFuture(null);
            }
        }

        manager.addMiddleware(new LoginHookMiddleware());

        LoginPacket login = new LoginPacket();
        manager.executePipeline(new MiddlewareContext<>(login, sessionHolder, null)).join();
        assertEquals(List.of("LOGIN"), log);

        CommandRequestPacket cmd = new CommandRequestPacket();
        manager.executePipeline(new MiddlewareContext<>(cmd, sessionHolder, null)).join();
        assertEquals(List.of("LOGIN", "COMMAND_DYNAMIC"), log);

        PlayerSessionHolder otherHolder = new PlayerSessionHolder(null, rateLimits);
        manager.executePipeline(new MiddlewareContext<>(cmd, otherHolder, null)).join();
        assertEquals(List.of("LOGIN", "COMMAND_DYNAMIC"), log, "Other session should not have the dynamic session middleware");
    }

    @Test
    public void testJava25ScopedValueContextPropagation() {
        RateLimitSettings rateLimits = new RateLimitSettings();
        PlayerSessionHolder sessionHolder = new PlayerSessionHolder(null, rateLimits);

        AtomicInteger scopedVerified = new AtomicInteger();

        class ScopedContextMiddleware implements IMiddleware<TextPacket> {
            @Override
            public String getName() { return "ScopedVerifier"; }

            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<TextPacket> context) {
                var currentOpt = MiddlewareContext.current();
                assertTrue(currentOpt.isPresent(), "MiddlewareContext.current() should be bound via ScopedValue");
                assertSame(context, currentOpt.get(), "Scoped context should match the handling context");
                scopedVerified.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        }

        manager.addMiddleware(new ScopedContextMiddleware());

        TextPacket packet = new TextPacket();
        manager.executePipeline(new MiddlewareContext<>(packet, sessionHolder, null)).join();

        assertEquals(1, scopedVerified.get());
        assertFalse(MiddlewareContext.current().isPresent(), "Outside of middleware execution scope, ScopedValue should be unbound");
    }
}
