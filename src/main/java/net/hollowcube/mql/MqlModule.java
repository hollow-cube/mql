package net.hollowcube.mql;

import net.hollowcube.mql.util.MqlCompileError;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public sealed interface MqlModule permits MqlModuleImpl, MqlModuleImpl.Error {

    boolean isValid();

    @NotNull Collection<String> errors();

    /**
     * Creates a new instance of this module, and evaluates all initializer blocks.
     *
     * @return A new instance of this module.
     * @throws MqlCompileError if the module is not valid.
     */
    @NotNull Instance newInstance() throws MqlCompileError;

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
