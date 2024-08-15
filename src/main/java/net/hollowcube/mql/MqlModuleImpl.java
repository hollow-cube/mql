package net.hollowcube.mql;

import net.hollowcube.mql.util.MqlCompileError;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.util.Collection;
import java.util.List;

record MqlModuleImpl(@NotNull MethodHandle implConstructor) implements MqlModule {

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public @NotNull Collection<String> errors() {
        return List.of();
    }

    @Override
    public @NotNull Instance newInstance() {
        try {
            return (Instance) implConstructor.invokeExact();
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
        public @NotNull Instance newInstance() throws MqlCompileError {
            throw new MqlCompileError(errors);
        }
    }
}
