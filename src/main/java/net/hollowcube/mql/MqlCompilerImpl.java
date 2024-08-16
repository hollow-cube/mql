package net.hollowcube.mql;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class MqlCompilerImpl implements MqlCompiler {
    private static final AtomicInteger MODULE_COUNTER = new AtomicInteger(0);

    //todo libraries
    private final List<String> initializers = new ArrayList<>();

    @Override
    public void addLibrary(@NotNull Class<?> libraryClass, @NotNull String... names) {

    }

    @Override
    public void addInitializer(@NotNull String text) {

    }

    @Override
    public @NotNull <T> Unit<T> addScript(@NotNull Class<T> spec, @NotNull String text) {
        return null;
    }

    @Override
    public @NotNull MqlModule compile() {
        return null;
    }

    record UnitImpl<T>(@NotNull Class<T> type, int moduleIndex, int scriptIndex) implements Unit<T> {
    }
}
