package net.hollowcube.mql.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public record ForeignFunction(
        @NotNull String javaName,
        @NotNull String mqlName,
        @NotNull Class<?>[] paramTypes,
        @NotNull Class<?> returnType,
        boolean isStatic
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
        //todo
        return Map.of();
    }
}
