package net.hollowcube.mql.internal;

import net.hollowcube.mql.foreign.ContentErrorException;
import net.hollowcube.mql.foreign.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public record ForeignFunction(
        @NotNull String javaName,
        @NotNull String mqlName,
        @NotNull Class<?>[] paramTypes,
        @NotNull Class<?> returnType,
        boolean isStatic,
        boolean isContentErrorSource
) {
    private static final ClassValue<Map<String, ForeignFunction>> ANALYSIS_CACHE = new ClassValue<>() {
        @Override
        protected @NotNull Map<String, ForeignFunction> computeValue(@NotNull Class<?> type) {
            return collectForeignFunctions(type);
        }
    };

    public static @Nullable ForeignFunction lookup(@NotNull Class<?> type, @NotNull String name) {
        // Lower case because Molang is case-insensitive.
        return ANALYSIS_CACHE.get(type).get(name.toLowerCase(Locale.ROOT));
    }

    private static @NotNull Map<String, ForeignFunction> collectForeignFunctions(@NotNull Class<?> type) {
        var result = new HashMap<String, ForeignFunction>();
        for (var method : type.getMethods()) {
            boolean isStatic = (method.getModifiers() & Modifier.STATIC) != 0;

            var config = method.getAnnotation(Query.class);
            if (config == null) continue;

            boolean isContentErrorSource = false;
            for (var exceptionType : method.getExceptionTypes()) {
                if (exceptionType.equals(ContentErrorException.class)) {
                    isContentErrorSource = true;
                } else {
                    throw new RuntimeException("Query method may only throw ContentErrorException");
                }
            }

            for (var paramType : method.getParameterTypes()) {
                if (!paramType.equals(double.class) && !paramType.equals(boolean.class))
                    throw new RuntimeException("Query method parameters must be either double or boolean");
            }

            var molangName = config.value().trim();
            if (molangName.isEmpty())
                molangName = camelCaseToSnakeCase(method.getName());
            molangName = molangName.toLowerCase(Locale.ROOT);

            if (result.containsKey(molangName))
                throw new RuntimeException("Duplicate method: " + molangName);
            result.put(molangName, new ForeignFunction(
                    method.getName(), molangName,
                    method.getParameterTypes(),
                    method.getReturnType(),
                    isStatic,
                    isContentErrorSource
            ));
        }
        return result;
    }

    private static @NotNull String camelCaseToSnakeCase(@NotNull String camelCase) {
        var sb = new StringBuilder();
        for (var c : camelCase.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
