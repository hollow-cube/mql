package net.hollowcube.mql.internal;

import net.hollowcube.mql.ContentError;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestMqlCodeBuilderInVM {

    @Test
    void fullClassSingleInitializerVariable() throws Throwable {
        var builder = new MqlCodeBuilder();
        builder.addInitializer("v.x = 22", "init1");

        var varHolder = builder.varHolder().invokeExact(ContentError.NOOP_HANDLER);
        builder.lowerInitializer().invokeExact(varHolder);

        assertEquals(22, getField(varHolder, "x"));
    }

    @Test
    void fullClassSingleInitializerMultipleVariables() throws Throwable {
        var builder = new MqlCodeBuilder();
        builder.addInitializer("v.x = 22; v.y = 33; v.z = v.x", "init1");

        var varHolder = builder.varHolder().invokeExact(ContentError.NOOP_HANDLER);
        builder.lowerInitializer().invokeExact(varHolder);

        assertEquals(22, getField(varHolder, "x"));
        assertEquals(33, getField(varHolder, "y"));
        assertEquals(22, getField(varHolder, "z"));
    }

    @Test
    void fullClassSingleScript() throws Throwable {
        var builder = new MqlCodeBuilder();
        builder.addScript(ScriptInterfaceEmpty.class, "42", "emptyScript", true);

        var varHolder = builder.varHolder().invokeExact(ContentError.NOOP_HANDLER);
        var script = builder.lowerScript(0).invokeExact(varHolder);

        assertEquals(42., ((ScriptInterfaceEmpty) script).eval());
    }

    @Test
    void fullClassScriptInitializerSharedVars() throws Throwable {
        var builder = new MqlCodeBuilder();
        builder.addInitializer("v.x = 22", "init1");
        builder.addScript(ScriptInterfaceEmpty.class, "v.x + 2", "theScript", true);

        var varHolder = builder.varHolder().invokeExact(ContentError.NOOP_HANDLER);
        builder.lowerInitializer().invokeExact(varHolder);
        var script = builder.lowerScript(0).invokeExact(varHolder);

        assertEquals(24., ((ScriptInterfaceEmpty) script).eval());
    }

    private static double getField(@NotNull Object instance, @NotNull String fieldName) throws Exception {
        var field = instance.getClass().getDeclaredField(fieldName);
        assertNotNull(field);
        field.setAccessible(true);
        return field.getDouble(instance);
    }

}
