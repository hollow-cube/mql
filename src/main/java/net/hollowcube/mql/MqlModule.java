package net.hollowcube.mql;

import net.hollowcube.mql.util.MqlCompileError;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public sealed interface MqlModule permits MqlModuleImpl, MqlModuleImpl.Error {

    boolean isValid();

    @NotNull Collection<String> errors();

    /**
     * <p>Creates a new instance of this module, and evaluates all initializer blocks.</p>
     *
     * <p>Content errors will be ignored for this instance.</p>
     *
     * @return A new instance of this module.
     * @throws MqlCompileError if the module is not valid.
     * @see #newInstance(ContentError.Handler)
     */
    default @NotNull Instance newInstance() throws MqlCompileError {
        return newInstance(null);
    }

    /**
     * Creates a new instance of this module, and evaluates all initializer blocks.
     *
     * @param contentErrorHandler The handler for content errors produced during script execution.
     * @return A new instance of this module.
     * @throws MqlCompileError if the module is not valid.
     * @see ContentError
     */
    @NotNull Instance newInstance(@Nullable ContentError.Handler contentErrorHandler) throws MqlCompileError;

    @ApiStatus.NonExtendable
    interface Instance {

        /**
         * Gets the given callable script from this module instance. Multiple calls to this method with the same ref
         * will return the same rhs for the lifetime of this instance.
         *
         * @param ref The script ref to get.
         * @param <T> The script interface.
         * @return The callable script.
         */
        <T> @NotNull T getScript(@NotNull MqlCompiler.Unit<T> ref);

    }

}
