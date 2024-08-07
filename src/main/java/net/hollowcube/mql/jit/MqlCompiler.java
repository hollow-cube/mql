package net.hollowcube.mql.jit;

import net.hollowcube.mql.foreign.Query;
import net.hollowcube.mql.parser.MqlParser;
import net.hollowcube.mql.runtime.MqlMath;
import net.hollowcube.mql.tree.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.objectweb.asm.Opcodes.*;

/**
 * A compiler for MQL scripts.
 * <p>
 * Some reflection calls are cached, so this object should be reused as much as possible.
 * <p>
 * Note: This class is not thread-safe, and must be synchronized externally.
 */
public class MqlCompiler<T> {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ClassInfo MATH_CI = new ClassInfo(MqlMath.class, true);

    private final MethodHandles.Lookup lookup;
    private final Class<?> scriptInterface;
    private final Class<?>[] generics;

    private Method evalMethod;
    private ParamInfo[] evalMethodParams;
    private String evalFnDescriptor;

    public MqlCompiler(@NotNull MethodHandles.Lookup lookup, Class<T> scriptInterface, Class<?>... generics) {
        this.lookup = lookup;
        this.scriptInterface = scriptInterface;
        this.generics = generics;
        parseScriptInterface();
    }

    public Class<T> compile(String script) {
        String sourceHash = Integer.toHexString(script.hashCode());
        // Could cache based on the hash if compiling many times over is a valid use case, but I don't think it is.

        String className = String.format("mql$%s$%d", sourceHash, COUNTER.getAndIncrement());
        byte[] bytecode = compileBytecode(className, script);
        return (Class<T>) AsmUtil.loadClass(className, bytecode);
    }

    @TestOnly
    public byte[] compileBytecode(String className, String script) {

        // Parse to an expression tree
        MqlExpr expr = new MqlParser(script).parse();

        // Create the class
        ClassWriter scriptClass = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        var sig = new StringBuilder();
        sig.append('L').append(AsmUtil.toName(scriptInterface)).append('<');
        for (Class<?> generic : generics)
            sig.append(AsmUtil.toDescriptor(generic));
        sig.append(">;");
        var targetPackage = scriptInterface.getPackageName().replace('.', '/');
        scriptClass.visit(V17, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, targetPackage + "/" + className, sig.toString(),
                AsmUtil.toName(Object.class), new String[]{AsmUtil.toName(scriptInterface)});

        // Generate required synthetics
        generateSynthetics(className, scriptClass);

        // Generate evaluate method
        MethodVisitor mv = scriptClass.visitMethod(ACC_PUBLIC, evalMethod.getName(), evalFnDescriptor, null, null);
        mv.visitCode();
        new BytecodeGeneratingVisitor(className, mv).visit(expr, null);
        mv.visitInsn(DRETURN); // ok because all expressions leave a double on the stack
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // Finish script class and return it
        scriptClass.visitEnd();
        return scriptClass.toByteArray();
    }


    /**
     * Ast visitor that generates bytecode for the given expression in the given method.
     * <p>
     * Note: Every expression _must_ leave a double on the stack. If the expression has no result, it should leave 0.
     */
    private class BytecodeGeneratingVisitor implements MqlVisitor<Void, Void> {
        private final String className;
        private final MethodVisitor method;

        private BytecodeGeneratingVisitor(@NotNull String className, @NotNull MethodVisitor method) {
            this.className = className;
            this.method = method;
        }

        @Override
        public Void visitBinaryExpr(@NotNull MqlBinaryExpr expr, Void unused) {
            // Visit both sides, resulting in two doubles on the stack
            visit(expr.lhs(), null);
            visit(expr.rhs(), null);

            // Perform the operation
            switch (expr.operator()) {
                case PLUS -> method.visitInsn(DADD);
                case MINUS -> method.visitInsn(DSUB);
                case MUL -> method.visitInsn(DMUL);
                case DIV -> method.visitInsn(DDIV);
                case NULL_COALESCE -> {
                    throw new RuntimeException("Null coalesce operator not supported in JIT mode");
                }
                case GTE ->
                        method.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "gte", "(DD)D", false);
                case GE -> method.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "ge", "(DD)D", false);
                case LTE ->
                        method.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "lte", "(DD)D", false);
                case LE -> method.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "le", "(DD)D", false);
                case EQ -> method.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "eq", "(DD)D", false);
                case NEQ ->
                        method.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "neq", "(DD)D", false);
            }

            return null;
        }

        @Override
        public Void visitAccessExpr(@NotNull MqlAccessExpr expr, Void unused) {
            // For now treat this as a zero args call, since we do not yet support assignment or field operators.
            if (!(expr.lhs() instanceof MqlIdentExpr ident))
                throw new UnsupportedOperationException("Nested queries are not supported");
            handleCall(ident.value(), expr.target(), new MqlArgListExpr(List.of()));
            return null;
        }

        @Override
        public Void visitCallExpr(MqlCallExpr expr, Void unused) {
            if (!(expr.access().lhs() instanceof MqlIdentExpr ident))
                throw new UnsupportedOperationException("Nested queries are not supported");

            handleCall(ident.value(), expr.access().target(), expr.argList());
            return null;
        }

        private void handleCall(String queryName, String methodName, MqlArgListExpr args) {
            // If this is a math call, it will not be a known parameter
            if ("math".equals(queryName) || "m".equals(queryName)) {
                var method = MATH_CI.findMethod(methodName, args.size());
                if (method == null) {
                    throw new UnsupportedOperationException("Method not found with " + args.size() + ": " + methodName);
                }

                for (int i = 0; i < args.size(); i++) {
                    visit(args.args().get(i), null);
                    AsmUtil.convert(method.getParameterTypes()[i], double.class, this.method);
                }

                // Call the method
                this.method.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlMath.class), method.getName(), AsmUtil.toDescriptor(method), false);
                return;
            }

            // Try to find a matching context object from script interface parameters.
            for (int j = 0; j < evalMethodParams.length; j++) {
                var param = evalMethodParams[j];
                boolean found = false;
                for (var name : param.names) {
                    if (name.equals(queryName)) {
                        found = true;
                        break;
                    }
                }

                if (!found) continue;

                // Found the query object being referenced
                var method = param.ci.findMethod(methodName, args.size());
                if (method == null) {
                    throw new UnsupportedOperationException("Method not found with " + args.size() + ": " + methodName);
                }

                // Load the query object
                this.method.visitVarInsn(ALOAD, j + 1);

                // Load the parameters
                for (int i = 0; i < args.size(); i++) {
                    visit(args.args().get(i), null);
                    AsmUtil.convert(method.getParameterTypes()[i], double.class, this.method);
                }

                // Call the method
                this.method.visitMethodInsn(INVOKEVIRTUAL, AsmUtil.toName(param.ci.type), method.getName(), AsmUtil.toDescriptor(method), false);

                return;
            }

            throw new RuntimeException("Unknown query object: " + queryName);
        }

        @Override
        public Void visitUnaryExpr(@NotNull MqlUnaryExpr expr, Void unused) {
            visit(expr.rhs(), null);

            switch (expr.operator()) {
                case NEGATE -> method.visitInsn(DNEG);
            }

            return null;
        }

        @Override
        public Void visitTernaryExpr(MqlTernaryExpr expr, Void unused) {
            //todo this is a cursed implementation. It needs to be implemented in java for sanity,
            // but also because this is semantically incorrect. The ternary operator is not short-circuiting.
            visit(expr.condition(), null);
            visit(expr.trueCase(), null);
            visit(expr.falseCase(), null);

            method.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "ternary", "(DDD)D", false);

            return null;
        }

        @Override
        public Void visitNumberExpr(@NotNull MqlNumberExpr expr, Void unused) {
            double value = expr.value().value();
            if (value == 0) {
                method.visitInsn(DCONST_0);
            } else if (value == 1) {
                method.visitInsn(DCONST_1);
            } else {
                method.visitLdcInsn(value);
            }
            return null;
        }

    }

    private void parseScriptInterface() {
        if (!scriptInterface.isInterface())
            throw new IllegalArgumentException("Script interface must be an interface");

        // Eval method
        var fnDesc = new StringBuilder().append('(');
        for (var method : scriptInterface.getMethods()) {
            if ((method.getModifiers() & Modifier.ABSTRACT) == 0) continue;
            if (evalMethod != null) throw new IllegalArgumentException("Script interface must have exactly one abstract method");
            evalMethod = method;
        }
        if (evalMethod == null) throw new IllegalArgumentException("Script interface must have exactly one abstract");
        evalMethodParams = new ParamInfo[evalMethod.getParameterCount()];
        int genericIndex = 0;
        for (int i = 0; i < evalMethod.getParameterCount(); i++) {
            var param = evalMethod.getParameters()[i];
            MqlEnv env = param.getAnnotation(MqlEnv.class);
            if (env == null) throw new IllegalArgumentException("Script interface parameters must be annotated with @MqlEnv");

            if (param.getParameterizedType() instanceof ParameterizedType) {
                if (genericIndex >= generics.length) throw new IllegalArgumentException("Too many generic parameters");
                evalMethodParams[i] = new ParamInfo(env.value(), new ClassInfo(generics[genericIndex++]), true);
            } else {
                evalMethodParams[i] = new ParamInfo(env.value(), new ClassInfo(param.getType()), false);
            }

            fnDesc.append(AsmUtil.toDescriptor(evalMethodParams[i].ci.type));
        }
        evalFnDescriptor = fnDesc.append(")D").toString();

        // Generic types
        if (generics.length != scriptInterface.getTypeParameters().length)
            throw new IllegalArgumentException("Script interface must have the same number of generic types as the generics parameter");

        // Return type
        if (evalMethod.getReturnType() != double.class)
            throw new IllegalArgumentException("Script interface must return a double");
    }

    private void generateSynthetics(@NotNull String className, @NotNull ClassVisitor cv) {
        // Add empty constructor
        var mv = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, AsmUtil.toName(Object.class), "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // Insert bridge method for eval method if it has any generic parameters
        if (generics.length != 0) {
            var desc = new StringBuilder();
            desc.append('(');
            for (ParamInfo param : evalMethodParams) {
                if (param.isGeneric) desc.append(AsmUtil.toDescriptor(Object.class));
                else desc.append(AsmUtil.toDescriptor(param.ci.type));
            }
            desc.append(")D");

            mv = cv.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC | ACC_BRIDGE, evalMethod.getName(), desc.toString(), null, null);
            mv.visitCode();

            int paramIndex = 0;
            mv.visitVarInsn(ALOAD, paramIndex++); // this
            for (var param : evalMethodParams) {
                mv.visitVarInsn(ALOAD, paramIndex++);
                // If generic, cast to correct type
                if (param.isGeneric) {
                    mv.visitTypeInsn(CHECKCAST, AsmUtil.toName(param.ci.type));
                }
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, className, "evaluate", evalFnDescriptor, false);
            mv.visitInsn(DRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private record ParamInfo(String[] names, ClassInfo ci, boolean isGeneric) {
    }

    private static class ClassInfo {
        private final Class<?> type;
        private final boolean allowStatic;
        private final Map<String, List<Method>> methodsByName = new HashMap<>();

        private ClassInfo(Class<?> type) {
            this(type, false);
        }

        private ClassInfo(Class<?> type, boolean allowStatic) {
            this.type = type;
            this.allowStatic = allowStatic;
            discoverMethods(type);
        }

        public Class<?> getType() {
            return type;
        }

        public Method findMethod(String name, int params) {
            for (var method : methodsByName.getOrDefault(name, List.of())) {
                if (method.getParameterCount() == params)
                    return method;
            }

            return null;
        }

        private void discoverMethods(Class<?> type) {
            for (var method : type.getMethods()) {
                if (!allowStatic && (method.getModifiers() & Modifier.STATIC) != 0) continue;
                if (!method.isAnnotationPresent(Query.class)) continue;
                for (var paramType : method.getParameterTypes()) {
                    if (!paramType.equals(double.class) && !paramType.equals(boolean.class))
                        throw new RuntimeException("Query method parameters must be either double or boolean");
                }
                methodsByName.computeIfAbsent(method.getName(), k -> new ArrayList<>())
                        .add(method);
            }
        }
    }
}
