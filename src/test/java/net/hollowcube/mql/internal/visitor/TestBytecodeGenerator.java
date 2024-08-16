package net.hollowcube.mql.internal.visitor;

import net.hollowcube.mql.jit.AsmUtil;
import net.hollowcube.mql.parser.MqlParser;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.V21;

public class TestBytecodeGenerator {

    @Test
    public void singleNumber() {
        assertCompilation("0", """
                DCONST_0
                """);
        assertCompilation("1", """
                DCONST_1
                """);
        assertCompilation("1.234", """
                LDC 1.234
                """);
    }

    @Test
    public void notUnary() {
        assertCompilation("-1", """
                DCONST_1
                DNEG
                """);
    }

    @Test
    public void simpleAddition() {
        assertCompilation("1 + 1", """
                DCONST_1
                DCONST_1
                DADD
                """);
    }


    @Test
    public void ternaryFull() {
        assertCompilation("1 == 2 ? 3 : 4", """
                DCONST_1
                LDC 2.0
                INVOKESTATIC net/hollowcube/mql/internal/MqlRuntime.eq (DD)D
                DCONST_0
                DCMPL
                IFEQ L0
                LDC 3.0
                GOTO L1
                L0
                LDC 4.0
                L1
                """);
    }

    @Test
    public void variableAssign() {
        assertCompilation(List.of("x"), "t.x = 1", """
                DCONST_1
                DSTORE 1
                """);
    }

    private void assertCompilation(@NotNull String source, @NotNull String expected) {
        assertCompilation(List.of(), source, expected);
    }

    private void assertCompilation(@NotNull List<String> locals, @NotNull String source, @NotNull String expected) {
        var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V21, ACC_PUBLIC, "net/hollowcube/mql/TestBytecodeGenerator", null, "java/lang/Object", null);
        var mv = cw.visitMethod(ACC_PUBLIC, "evaluate", "()D", null, null);

        var expr = new MqlParser(source).parse();
        new BytecodeGenerator(mv).visit(expr, locals);

        mv.visitEnd();

        cw.visitEnd();

        var str = AsmUtil.prettyPrintEvalMethod(cw.toByteArray());
        assertEquals(expected, str);
    }
}
