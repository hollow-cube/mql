package net.hollowcube.mql;

import net.hollowcube.mql.internal.MqlCodeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.List;

final class MqlCompilerImpl implements MqlCompiler {
    private final MqlCodeBuilder builder = new MqlCodeBuilder();
    private int scriptIndex = 0;

    @Override
    public void addLibrary(@NotNull Class<?> libraryClass, @NotNull String... names) {
        builder.addLibrary(libraryClass, names);
    }

    @Override
    public void addInitializer(@NotNull String text, @Nullable String debugName) {
        builder.addInitializer(text, debugName);
    }

    @Override
    public @NotNull <T> Unit<T> addScript(
            @NotNull Class<T> spec, @NotNull String text,
            boolean isSimpleExpr, @Nullable String debugName
    ) {
        builder.addScript(spec, text, debugName, isSimpleExpr);
        return new UnitImpl<>(spec, builder.moduleIndex(), scriptIndex++);
    }

    @Override
    public @NotNull MqlModule compile() {
        if (!builder.errors().isEmpty()) {
            return new MqlModuleImpl.Error(builder.errors());
        }

        var varHolderConstructor = builder.varHolder();
        var initializerMethod = builder.lowerInitializer();
        var scripts = new MethodHandle[scriptIndex];
        for (int i = 0; i < scriptIndex; i++)
            scripts[i] = builder.lowerScript(i);

        return new MqlModuleImpl(
                builder.moduleIndex(),
                varHolderConstructor,
                initializerMethod,
                List.of(scripts)
        );
    }

    record UnitImpl<T>(@NotNull Class<T> type, int moduleIndex, int scriptIndex) implements Unit<T> {
    }
}
