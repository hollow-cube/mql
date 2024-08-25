package net.hollowcube.mql;

import net.hollowcube.mql.util.MqlCompileError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

record MqlModuleImpl(
        int moduleIndex,
        @NotNull MethodHandle varHolderConstructor,
        @NotNull MethodHandle initializer,
        @NotNull List<MethodHandle> scriptImpls
) implements MqlModule {

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public @NotNull Collection<String> errors() {
        return List.of();
    }

    @Override
    public @NotNull Instance newInstance(@Nullable ContentError.Handler contentErrorHandler) {
        try {
            var errorHandler = Objects.requireNonNullElse(contentErrorHandler, ContentError.NOOP_HANDLER);
            Object varHolder = varHolderConstructor.invokeExact(errorHandler);
            initializer.invokeExact(varHolder);
            List<Object> scripts = new ArrayList<>(scriptImpls.size());
            for (MethodHandle scriptImpl : scriptImpls)
                scripts.add(scriptImpl.invokeExact(varHolder));
            return new InstanceImpl(moduleIndex, varHolder, List.copyOf(scripts));
        } catch (Throwable t) {
            throw new RuntimeException("failed to create instance", t);
        }
    }

    record Error(@NotNull Collection<String> errors) implements MqlModule {
        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public @NotNull Instance newInstance(@Nullable ContentError.Handler contentErrorHandler) throws MqlCompileError {
            throw new MqlCompileError(errors);
        }
    }

    record InstanceImpl(
            int moduleIndex,
            @NotNull Object varHolder,
            @NotNull List<Object> scripts
    ) implements MqlModule.Instance {
        @Override
        public <T> @NotNull T getScript(MqlCompiler.@NotNull Unit<T> ref) {
            var refImpl = ((MqlCompilerImpl.UnitImpl<T>) ref);
            if (refImpl.moduleIndex() != moduleIndex)
                throw new IllegalArgumentException("invalid module index");
            return refImpl.type().cast(scripts.get(refImpl.scriptIndex()));
        }
    }
}
