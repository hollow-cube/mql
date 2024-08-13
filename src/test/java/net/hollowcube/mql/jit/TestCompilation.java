package net.hollowcube.mql.jit;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCompilation {

    @Test
    public void singleNumber() {
        check(BaseScript.class, "0", """
                DCONST_0
                DRETURN
                """);
        check(BaseScript.class, "1", """
                DCONST_1
                DRETURN
                """);
        check(BaseScript.class, "1.234", """
                LDC 1.234
                DRETURN
                """);
    }

    @Test
    public void simpleAddition() {
        check(BaseScript.class, "1 + 1", """
                DCONST_1
                DCONST_1
                DADD
                DRETURN
                """);
    }

    @Test
    public void ternary() {
        check(BaseScript.class, "1 == 2 ? 3 : 4", """
                DCONST_1
                LDC 2.0
                INVOKESTATIC net/hollowcube/mql/jit/MqlRuntime.eq (DD)D
                DCONST_0
                DCMPL
                IFEQ L0
                LDC 3.0
                GOTO L1
                L0
                LDC 4.0
                L1
                DRETURN
                """);
    }

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