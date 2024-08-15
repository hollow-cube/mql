package net.hollowcube.mql.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class MqlCompileError extends RuntimeException {
    private final Collection<String> errors;

    public MqlCompileError(@NotNull Collection<String> errors) {
        super("Compilation failed: " + errors.toString());
        this.errors = errors;
    }

    public @NotNull Collection<String> errors() {
        return errors;
    }
}
