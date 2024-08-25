package net.hollowcube.mql.internal;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

public final class AsmUtil {
    private AsmUtil() {
    }

    public static @NotNull String methodDescriptor(@NotNull Class<?> returnType, @NotNull Class<?>... param) {
        var sb = new StringBuilder("(");
        for (var p : param) {
            sb.append(toDescriptor(p));
        }
        sb.append(")");
        sb.append(toDescriptor(returnType));
        return sb.toString();
    }

    public static @NotNull String toDescriptor(@NotNull Class<?> clazz) {
        if (clazz.isArray()) {
            return "[" + toDescriptor(clazz.getComponentType());
        }
        if (boolean.class.equals(clazz)) {
            return "Z";
        } else if (byte.class.equals(clazz)) {
            return "B";
        } else if (char.class.equals(clazz)) {
            return "C";
        } else if (short.class.equals(clazz)) {
            return "S";
        } else if (int.class.equals(clazz)) {
            return "I";
        } else if (long.class.equals(clazz)) {
            return "J";
        } else if (float.class.equals(clazz)) {
            return "F";
        } else if (double.class.equals(clazz)) {
            return "D";
        } else if (void.class.equals(clazz)) {
            return "V";
        }
        return "L" + toName(clazz) + ";";
    }

    public static @NotNull String toDescriptor(@NotNull Method method) {
        var sb = new StringBuilder("(");
        for (var param : method.getParameterTypes()) {
            sb.append(toDescriptor(param));
        }
        sb.append(")");
        sb.append(toDescriptor(method.getReturnType()));
        return sb.toString();
    }

    public static @NotNull String toName(@NotNull Class<?> clazz) {
        return clazz.getName().replace(".", "/");
    }

    public static void convert(Class<?> to, Class<?> from, MethodVisitor mv) {
        if (to.isAssignableFrom(from)) {
            return;
        }
        //todo handle other conversions
        throw new UnsupportedOperationException("Cannot convert from " + from + " to " + to);
    }

    public static @NotNull String prettyPrintEvalMethod(byte[] bytecode) {
        var str = new StringBuilder();
        var printer = new Textifier();
        var mp = new TraceMethodVisitor(printer);

        var cr = new ClassReader(bytecode);
        var cn = new ClassNode();
        cr.accept(cn, 0);
        for (var method : cn.methods) {
            // Ignore any method that isnt evaluate and not a bridge (we want the concrete impl)
            if (!"evaluate".equals(method.name) || (method.access & Opcodes.ACC_BRIDGE) != 0)
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
