package net.hollowcube.mql.jit;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCompilation {


    @Test
    public void callQuerySingleArg() {
        check(QueryScript.class, "q.dbl(1.0)", """
                ALOAD 1
                DCONST_1
                INVOKEVIRTUAL net/hollowcube/mql/jit/QueryTest.dbl (D)D
                DRETURN
                """);
    }

    @Test
    public void callQueryMultiArg() {
        check(QueryScript.class, "q.mul(1.0, 2.0)", """
                ALOAD 1
                DCONST_1
                LDC 2.0
                INVOKEVIRTUAL net/hollowcube/mql/jit/QueryTest.mul (DD)D
                DRETURN
                """);
    }

    @Test
    public void callQueryInnerCall() {
        check(QueryScript.class, "q.mul(2.0, q.dbl(2.0))", """
                ALOAD 1
                LDC 2.0
                ALOAD 1
                LDC 2.0
                INVOKEVIRTUAL net/hollowcube/mql/jit/QueryTest.dbl (D)D
                INVOKEVIRTUAL net/hollowcube/mql/jit/QueryTest.mul (DD)D
                DRETURN
                """);
    }

    private void check(@NotNull Class<?> script, @NotNull String source, @NotNull String expected) {
        var compiler = new MqlCompiler<>(MethodHandles.lookup(), script);
        byte[] bytecode = compiler.compileBytecode("mql$test", source);

        var str = AsmUtil.prettyPrintEvalMethod(bytecode);
        assertEquals(expected, str);
    }
}