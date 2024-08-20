package net.hollowcube.mql.internal.visitor;

import net.hollowcube.mql.jit.AsmUtil;
import net.hollowcube.mql.parser.MqlParser;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;

import java.util.List;
import java.util.Map;

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
    public void simpleBinaryOps() {
        assertCompilation("1 + 1", """
                DCONST_1
                DCONST_1
                DADD
                """);
        assertCompilation("1 - 1", """
                DCONST_1
                DCONST_1
                DSUB
                """);
        assertCompilation("1 * 1", """
                DCONST_1
                DCONST_1
                DMUL
                """);
        assertCompilation("1 / 1", """
                DCONST_1
                DCONST_1
                DDIV
                """);
    }

    @Test
    public void complexBinaryOps() {
        assertCompilation("3 == 4", """
                LDC 3.0
                LDC 4.0
                DCMPL
                IFEQ L0
                DCONST_0
                GOTO L1
                L0
                DCONST_1
                L1
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
    public void ternaryTrueCaseOnly() {
        assertCompilation("1 == 2 ? 3", """
                DCONST_1
                LDC 2.0
                INVOKESTATIC net/hollowcube/mql/internal/MqlRuntime.eq (DD)D
                DCONST_0
                DCMPL
                IFEQ L0
                LDC 3.0
                GOTO L1
                L0
                DCONST_0
                L1
                """);
    }

    @Test
    public void localVariableAssign() {
        assertCompilation(List.of("x"), "t.x = 1", """
                DCONST_1
                DSTORE 1
                """);
    }

    @Test
    public void localVariableRead() {
        assertCompilation(List.of("x"), "t.x", """
                DLOAD 1
                """);
    }

    private void assertCompilation(@NotNull String source, @NotNull String expected) {
        assertCompilation(List.of(), source, expected);
    }

    private void assertCompilation(@NotNull List<String> locals, @NotNull String source, @NotNull String expected) {
        var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V21, ACC_PUBLIC, "net/hollowcube/mql/TestBytecodeGenerator", null, "java/lang/Object", null);
        var mv = cw.visitMethod(ACC_PUBLIC, "evaluate", "()D", null, null);

        var expr = new MqlParser(source, true).parse();
        new BytecodeGenerator(mv, Map.of()).visit(expr, locals);

        mv.visitEnd();

        cw.visitEnd();

        var str = AsmUtil.prettyPrintEvalMethod(cw.toByteArray());
        assertEquals(expected, str);
    }
}
