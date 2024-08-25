package net.hollowcube.mql.internal;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestMqlCodeBuilder {

    @Test
    void ensureVarHolderHasVars() throws Exception {
        var builder = new MqlCodeBuilder();
        builder.addInitializer("v.x = 1", "init1");

        var varHolderClass = builder.varHolderClass();
        assertNotNull(varHolderClass.getDeclaredField("x"));
    }

    @Test
    void fullClassSingleInitializerOfVariable() {
        var builder = new MqlCodeBuilder();
        builder.addInitializer("v.x = 1", "init1");

        var initializer = builder.lowerInitializerCode();

        assertEquals("""
                        // class version 65.0 (65)
                        // access flags 0x1011
                        public final synthetic class net/hollowcube/mql/internal/Initializer {
                        
                        
                          // access flags 0x9
                          public static modInit(Lnet/hollowcube/mql/internal/VarHolder$120;)V
                            DCONST_1
                            DUP2
                            ALOAD 0
                            DUP_X2
                            POP
                            PUTFIELD net/hollowcube/mql/internal/VarHolder$120.x : D
                            POP2
                            DCONST_0
                            POP2
                            RETURN
                            MAXSTACK = 6
                            MAXLOCALS = 1
                        }
                        """,
                classToString(initializer));
    }

    @Test
    void fullClassSingleScript() {
        var builder = new MqlCodeBuilder();
        builder.addScript(ScriptInterfaceEmpty.class, "1", "emptyScript", true);

        var scriptCode = builder.lowerScriptCode(0);

        assertEquals("""
                        // class version 65.0 (65)
                        // access flags 0x1011
                        public final synthetic class net/hollowcube/mql/internal/Script0 implements net/hollowcube/mql/internal/ScriptInterfaceEmpty {
                        
                        
                          // access flags 0x12
                          private final Lnet/hollowcube/mql/internal/VarHolder$0; mql$vars
                        
                          // access flags 0x1
                          public <init>(Lnet/hollowcube/mql/internal/VarHolder$0;)V
                            ALOAD 0
                            INVOKESPECIAL java/lang/Object.<init> ()V
                            ALOAD 0
                            ALOAD 1
                            PUTFIELD net/hollowcube/mql/internal/Script0.mql$vars : Lnet/hollowcube/mql/internal/VarHolder$0;
                            RETURN
                            MAXSTACK = 2
                            MAXLOCALS = 2
                        
                          // access flags 0x1
                          public eval()D
                            DCONST_1
                            DRETURN
                            MAXSTACK = 2
                            MAXLOCALS = 1
                        }
                        """,
                classToString(scriptCode));
    }

    @Test
    void fullClassCustomScript() {
        var builder = new MqlCodeBuilder();
        builder.addScript(ScriptInterfaceQuery.class, "q.inc(1)", "theScript", true);

        var scriptCode = builder.lowerScriptCode(0);

        assertEquals("""
                        DCONST_1
                        ALOAD 1
                        INVOKEVIRTUAL net/hollowcube/mql/internal/ScriptInterfaceQuery$MyQuery.inc (D)D
                        DRETURN
                        """,
                classMethodToString(scriptCode, "eval"));
    }

    private static @NotNull String classToString(byte[] bytecode) {
        StringWriter sw = new StringWriter();
        var cp = new TraceClassVisitor(new PrintWriter(sw));
        var cr = new ClassReader(bytecode);
        cr.accept(cp, 0);
        return sw.toString();
    }

    private static @NotNull String classMethodToString(byte[] bytecode, @NotNull String methodName) {
        var str = new StringBuilder();
        var printer = new Textifier();
        var mp = new TraceMethodVisitor(printer);

        var cr = new ClassReader(bytecode);
        var cn = new ClassNode();
        cr.accept(cn, 0);
        for (var method : cn.methods) {
            // Ignore any method that isnt the target and not a bridge (we want the concrete impl)
            if (!methodName.equals(method.name) || (method.access & Opcodes.ACC_BRIDGE) != 0)
                continue;
            InsnList insns = method.instructions;
            for (var insn : insns) {
                insn.accept(mp);
                StringWriter sw = new StringWriter();
                printer.print(new PrintWriter(sw));
                printer.getText().clear();
                str.append(sw.toString().trim()).append("\n");
            }
        }

        return str.toString();
    }
}
