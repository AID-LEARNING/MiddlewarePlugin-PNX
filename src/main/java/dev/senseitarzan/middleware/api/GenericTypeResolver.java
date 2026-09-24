package dev.senseitarzan.middleware.api;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to automatically infer the generic packet type {@code <T>}
 * from classes implementing {@link IMiddleware}.
 */
public final class GenericTypeResolver {

    private static final Map<Class<?>, Class<? extends BedrockPacket>> CACHE = new ConcurrentHashMap<>();

    private GenericTypeResolver() {
    }

    /**
     * Resolves the packet class {@code <T extends BedrockPacket>} from the class hierarchy.
     *
     * @param clazz the middleware class
     * @param <T> the packet type
     * @return the resolved BedrockPacket class, or null if unresolvable
     */
    @SuppressWarnings("unchecked")
    public static <T extends BedrockPacket> Class<T> resolvePacketClass(Class<?> clazz) {
        if (clazz == null || clazz.equals(IMiddleware.class)) {
            return null;
        }

        return (Class<T>) CACHE.computeIfAbsent(clazz, GenericTypeResolver::doResolve);
    }

    /**
     * Internal resolution logic extracting the generic packet type.
     *
     * @param clazz the class to inspect
     * @return the resolved BedrockPacket class, or null
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends BedrockPacket> doResolve(Class<?> clazz) {
        Type type = findGenericTypeArgument(clazz, IMiddleware.class, 0);
        if (type instanceof Class<?> c && BedrockPacket.class.isAssignableFrom(c)) {
            return (Class<? extends BedrockPacket>) c;
        }
        return null;
    }

    /**
     * Walks the class hierarchy and implemented interfaces to find the concrete generic type argument.
     *
     * @param currentClass the current class in the hierarchy
     * @param targetInterface the target interface to locate
     * @param argIndex the index of the type argument
     * @return the resolved Type or null
     */
    private static Type findGenericTypeArgument(Class<?> currentClass, Class<?> targetInterface, int argIndex) {
        Map<TypeVariable<?>, Type> typeVariableMap = new HashMap<>();
        Class<?> clazz = currentClass;

        while (clazz != null && clazz != Object.class) {
            for (Type genericInterface : clazz.getGenericInterfaces()) {
                Type resolved = checkType(genericInterface, targetInterface, argIndex, typeVariableMap);
                if (resolved != null) {
                    return resolveTypeVariable(resolved, typeVariableMap);
                }
            }

            Type genericSuperclass = clazz.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType pt) {
                if (pt.getRawType() instanceof Class<?> rawSuper) {
                    TypeVariable<?>[] typeParameters = rawSuper.getTypeParameters();
                    Type[] actualArgs = pt.getActualTypeArguments();
                    for (int i = 0; i < typeParameters.length && i < actualArgs.length; i++) {
                        typeVariableMap.put(typeParameters[i], resolveTypeVariable(actualArgs[i], typeVariableMap));
                    }
                    Type resolved = checkType(genericSuperclass, targetInterface, argIndex, typeVariableMap);
                    if (resolved != null) {
                        return resolveTypeVariable(resolved, typeVariableMap);
                    }
                    clazz = rawSuper;
                } else {
                    break;
                }
            } else if (genericSuperclass instanceof Class<?> c) {
                clazz = c;
            } else {
                break;
            }
        }
        return null;
    }

    /**
     * Checks if a type matches the target interface or is a parameterized subclass of it.
     *
     * @param type the type to inspect
     * @param targetInterface the target interface class
     * @param argIndex the type argument index
     * @param map mapping of type variables to concrete types
     * @return the resolved Type or null
     */
    private static Type checkType(Type type, Class<?> targetInterface, int argIndex, Map<TypeVariable<?>, Type> map) {
        if (type instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();
            if (raw instanceof Class<?> rawClass) {
                if (rawClass.equals(targetInterface)) {
                    Type[] args = pt.getActualTypeArguments();
                    if (args.length > argIndex) {
                        return resolveTypeVariable(args[argIndex], map);
                    }
                } else if (targetInterface.isAssignableFrom(rawClass)) {
                    TypeVariable<?>[] typeParams = rawClass.getTypeParameters();
                    Type[] actualArgs = pt.getActualTypeArguments();
                    for (int i = 0; i < typeParams.length && i < actualArgs.length; i++) {
                        map.put(typeParams[i], resolveTypeVariable(actualArgs[i], map));
                    }
                    for (Type gi : rawClass.getGenericInterfaces()) {
                        Type found = checkType(gi, targetInterface, argIndex, map);
                        if (found != null) {
                            return resolveTypeVariable(found, map);
                        }
                    }
                }
            }
        } else if (type instanceof Class<?> c && targetInterface.isAssignableFrom(c)) {
            for (Type gi : c.getGenericInterfaces()) {
                Type found = checkType(gi, targetInterface, argIndex, map);
                if (found != null) {
                    return resolveTypeVariable(found, map);
                }
            }
        }
        return null;
    }

    /**
     * Resolves a TypeVariable recursively through the type variable map.
     *
     * @param type the type to resolve
     * @param map the type variable mapping table
     * @return the resolved concrete type or original type
     */
    private static Type resolveTypeVariable(Type type, Map<TypeVariable<?>, Type> map) {
        while (type instanceof TypeVariable<?> tv) {
            Type mapped = map.get(tv);
            if (mapped == null || mapped == type) {
                break;
            }
            type = mapped;
        }
        return type;
    }
}
