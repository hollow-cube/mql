package net.hollowcube.mql.internal.visitor;

import net.hollowcube.mql.builtin.MqlMath;
import net.hollowcube.mql.internal.AsmUtil;
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
    }

    @Test
    public void divideByZeroNoCoalesce() {
        assertCompilation("1 / 0", """
                DCONST_1
                DCONST_0
                DUP
                DCONST_0
                DCMPL
                IFNE L0
                POP2
                POP2
                ALOAD 0
                GETFIELD net/hollowcube/mql/internal/VarHolder$TEST.mql$contentErrorHandler : Lnet/hollowcube/mql/ContentError$Handler;
                NEW net/hollowcube/mql/ContentError
                DUP
                ICONST_0
                LDC "Division by zero"
                INVOKESPECIAL net/hollowcube/mql/ContentError.<init> (ILnet/hollowcube/mql/ContentError;)V
                INVOKEINTERFACE net/hollowcube/mql/ContentError$Handler.handle (Lnet/hollowcube/mql/ContentError;)V (itf)
                DCONST_0
                GOTO L1
                L0
                DDIV
                L1
                """);
    }

    @Test
    public void divideByZeroCoalesced() {
        assertCompilation("(1 / 0) ?? 2", """
                DCONST_1
                DCONST_0
                DUP
                DCONST_0
                DCMPL
                IFNE L0
                POP2
                POP2
                LDC 2.0
                GOTO L1
                L0
                DDIV
                L1
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
                DCMPL
                IFEQ L0
                DCONST_0
                GOTO L1
                L0
                DCONST_1
                L1
                DCONST_0
                DCMPL
                IFEQ L2
                LDC 3.0
                GOTO L3
                L2
                LDC 4.0
                L3
                """);
    }

    @Test
    public void ternaryTrueCaseOnly() {
        assertCompilation("1 == 2 ? 3", """
                DCONST_1
                LDC 2.0
                DCMPL
                IFEQ L0
                DCONST_0
                GOTO L1
                L0
                DCONST_1
                L1
                DCONST_0
                DCMPL
                IFEQ L2
                LDC 3.0
                GOTO L3
                L2
                DCONST_0
                L3
                """);
    }

    @Test
    public void localVariableAssign() {
        assertCompilation(List.of("x"), "t.x = 1", """
                DCONST_1
                DUP2
                DSTORE 1
                """);
    }

    @Test
    public void localVariableRead() {
        assertCompilation(List.of("x"), "t.x", """
                DLOAD 1
                """);
    }

    @Test
    public void fieldVariableAssign() {
        // The bytecode for this looks kinda strange, check BytecodeGenerator#visitAssignExpr
        // for an explanation of why its like this.
        assertCompilation("v.x = 1", """
                DCONST_1
                DUP2
                ALOAD 0
                DUP_X2
                POP
                PUTFIELD net/hollowcube/mql/internal/VarHolder$TEST.x : D
                """);
    }

    @Test
    public void fieldVariableRead() {
        assertCompilation("v.x", """
                ALOAD 0
                GETFIELD net/hollowcube/mql/internal/VarHolder$TEST.x : D
                """);
    }

    @Test
    public void returns() {
        assertCompilation("return", """
                DCONST_0
                DRETURN
                """);
        assertCompilation("return 1.0", """
                DCONST_1
                DRETURN
                """);
    }

    @Test
    public void noArgsCall() {
        assertCompilation("m.pi", """
                INVOKESTATIC net/hollowcube/mql/builtin/MqlMath.pi ()D
                """);
    }

    @Test
    public void singleArgCall() {
        assertCompilation("m.ln(5)", """
                LDC 5.0
                INVOKESTATIC net/hollowcube/mql/builtin/MqlMath.ln (D)D
                """);
    }

    @Test
    public void callContentErrorSource() {
        assertCompilation("m.mod(5, 0)", """
                LDC 5.0
                DCONST_0
                L0
                INVOKESTATIC net/hollowcube/mql/builtin/MqlMath.mod (DD)D
                GOTO L1
                L2
                ALOAD 0
                GETFIELD net/hollowcube/mql/internal/VarHolder$TEST.mql$contentErrorHandler : Lnet/hollowcube/mql/ContentError$Handler;
                NEW net/hollowcube/mql/ContentError
                DUP
                ICONST_0
                INVOKEVIRTUAL net/hollowcube/mql/foreign/ContentErrorException.getMessage ()Ljava/lang/String;
                INVOKESPECIAL net/hollowcube/mql/ContentError.<init> (ILnet/hollowcube/mql/ContentError;)V
                INVOKEINTERFACE net/hollowcube/mql/ContentError$Handler.handle (Lnet/hollowcube/mql/ContentError;)V (itf)
                DCONST_0
                L1
                """);
    }

    @Test
    public void callContentErrorSourceWithCoalesce() {
        assertCompilation("m.mod(5, 0) ?? 21", """
                LDC 5.0
                DCONST_0
                L0
                INVOKESTATIC net/hollowcube/mql/builtin/MqlMath.mod (DD)D
                GOTO L1
                L2
                POP
                LDC 21.0
                L1
                """);
    }

    private void assertCompilation(@NotNull String source, @NotNull String expected) {
        assertCompilation(List.of(), source, expected);
    }

    private void assertCompilation(@NotNull List<String> locals, @NotNull String source, @NotNull String expected) {
        var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        var className = "net/hollowcube/mql/internal/VarHolder$TEST";
        cw.visit(V21, ACC_PUBLIC, className, null, "java/lang/Object", null);
        var mv = cw.visitMethod(ACC_PUBLIC, "evaluate", "()D", null, null);

        var expr = new MqlParser(source, true).parse();
        var mathContext = Map.<String, Map.Entry<Integer, Class<?>>>of(
                "m", Map.entry(-1, MqlMath.class), "math", Map.entry(-1, MqlMath.class));
        new BytecodeGenerator(mv, 1, null, className, mathContext).visit(expr, locals);

        mv.visitEnd();

        cw.visitEnd();

        var str = AsmUtil.prettyPrintEvalMethod(cw.toByteArray());
        assertEquals(expected, str);
    }
}
