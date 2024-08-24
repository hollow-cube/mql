package net.hollowcube.mql;

import net.hollowcube.mql.foreign.ContentErrorException;
import org.jetbrains.annotations.NotNull;

/**
 * <p>Represents a content error generated during script execution.</p>
 *
 * <p>In MQL, calls which would typically produce an error (eg, divide by zero) instead return zero
 * and emit a content error. Foreign functions may declare a checked {@link ContentErrorException} which
 * will be handled by the script as any other content error.</p>
 *
 * <p>Content errors can be handled on a per-instance basis by supplying a handler to {@link MqlModule#newInstance(Handler)}</p>
 */
public record ContentError(
        @NotNull MqlCompiler.Unit<?> script,
        @NotNull String message
        // TODO: Include source location for the offending call
) {

    /**
     * A callback when a {@link ContentError} is generated during script evaluation.
     *
     * <p>Note: The handler will be executed during script evaluation on the script eval thread. Exceptions thrown
     * in the handler will <b>not</b> be caught by MQL and will bubble up to the script caller.</p>
     */
    @FunctionalInterface
    public interface Handler {
        void handle(@NotNull ContentError error);
    }

}
