package net.hollowcube.mql;

import net.hollowcube.mql.builtin.MqlMath;
import net.hollowcube.mql.foreign.Query;
import net.hollowcube.mql.util.MqlCompileError;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public sealed interface MqlCompiler permits MqlCompilerImpl {

    /**
     * <p>Compiles a one-off script with no extra context. The builtin math library will always be present in addition
     * to any query objects in the provided script interface. The script will never share state with other scripts,
     * but may have its own variables and temporary variables.</p>
     *
     * <p>This function is a shorthand for the rest of this api, the implementation may provide some hints for
     * more advanced usage.</p>
     *
     * @param t            The script interface to implement.
     * @param text         The script text to evaluate.
     * @param isSimpleExpr True if the script is a simple expression (one expression, no ;), false for a complex
     *                     block which requires ; as well as a return statement to return a rhs.
     * @param <T>          The script interface to implement.
     * @return A supplier that will instantiate the script when called.
     * @throws MqlCompileError If there are errors in the script.
     */
    static <T> @NotNull Supplier<T> compile(@NotNull Class<T> t, @NotNull String text, boolean isSimpleExpr) {
        var compiler = create();
        var ref = compiler.addScript(t, text, isSimpleExpr);
        var module = compiler.compile();
        if (!module.isValid()) throw new MqlCompileError(module.errors());
        return () -> module.newInstance().getScript(ref);
    }

    static @NotNull MqlCompiler create() {
        throw new UnsupportedOperationException("todo");
    }

    /**
     * <p>Adds an implicit query object to the module. A valid library may only have static methods, and all public
     * methods must be annotated with {@link Query}. See {@link MqlMath}
     * for an example.</p>
     *
     * <p>This behavior acts identically to how the builtin math library works, and you may use this feature to
     * override the builtin math library. To do so, simply register a different class with the names "math" and "m".</p>
     *
     * @param libraryClass The class to add as a library.
     * @param names        The names to register the library under, eg "math" and "m" for the math library.
     */
    void addLibrary(@NotNull Class<?> libraryClass, @NotNull String... names);

    /**
     * Adds an initializer block to the module. This block will be executed when the module is instantiated. It is
     * generally used for initializing variables, and does not have target to any declared context objects.
     *
     * @param text The script text to evaluate.
     */
    void addInitializer(@NotNull String text);

    /**
     * Adds a script to the module. The script will have target to the query objects in its environment. The returned
     * script ref can be used to eval the script on an instance of the module after it has been compiled.
     *
     * @param spec         The script interface to implement.
     * @param text         The script text to evaluate.
     * @param isSimpleExpr True if the script is a simple expression (one expression, no ;), false for a complex
     *                     block which requires ; as well as a return statement to return a rhs.
     * @param <T>          The script interface to implement.
     * @return The compilation unit.
     */
    <T> @NotNull Unit<T> addScript(@NotNull Class<T> spec, @NotNull String text, boolean isSimpleExpr);

    /**
     * <p>Compile the module and return it. Compilation errors will not be thrown, you should inspect
     * {@link MqlModule#isValid()} and {@link MqlModule#errors()} for compilation errors.</p>
     *
     * <p>After this call, no other methods on this class are valid and all will throw an illegal state exception.</p>
     */
    @NotNull MqlModule compile();

    /**
     * A marker interface for an unknown-status script unit within a module.
     *
     * @param <T> The script interface implemented by the script.
     */
    sealed interface Unit<T> permits MqlCompilerImpl.UnitImpl {
        @NotNull Class<T> type();
    }

}
