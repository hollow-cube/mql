package net.hollowcube.mql.internal;

import net.hollowcube.mql.MqlCompiler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class MqlRuntime {
    private MqlRuntime() {
    }

    public static double gte(double lhs, double rhs) {
        return lhs >= rhs ? 1 : 0;
    }

    public static double ge(double lhs, double rhs) {
        return lhs > rhs ? 1 : 0;
    }

    public static double lte(double lhs, double rhs) {
        return lhs <= rhs ? 1 : 0;
    }

    public static double le(double lhs, double rhs) {
        return lhs < rhs ? 1 : 0;
    }

    public static double eq(double lhs, double rhs) {
        return lhs == rhs ? 1 : 0;
    }

    public static double neq(double lhs, double rhs) {
        return lhs != rhs ? 1 : 0;
    }

    public static <T> @NotNull T getScript(int instanceIndex, Object[] scripts, @NotNull MqlCompiler.Unit<T> unit) {
//        if (ref.moduleIndex() != instanceIndex)
//            throw new IllegalArgumentException("ScriptRef is from a different module instance.");
//        return unit.type().cast(scripts[ref.scriptIndex()]);
        throw new UnsupportedOperationException("todo");
    }

}
