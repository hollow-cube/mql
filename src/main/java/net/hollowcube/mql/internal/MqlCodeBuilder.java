package net.hollowcube.mql.internal;

import net.hollowcube.mql.ContentError;
import net.hollowcube.mql.builtin.MqlMath;
import net.hollowcube.mql.foreign.MqlEnv;
import net.hollowcube.mql.internal.tree.MqlExpr;
import net.hollowcube.mql.internal.visitor.BytecodeGenerator;
import net.hollowcube.mql.internal.visitor.VariableExtractionVisitor;
import net.hollowcube.mql.parser.MqlParseError;
import net.hollowcube.mql.parser.MqlParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static net.hollowcube.mql.internal.AsmUtil.*;
import static org.objectweb.asm.Opcodes.*;


public class MqlCodeBuilder {
    public static final String VAR_HOLDER_FIELD_NAME = "mql$vars";
    public static final String CONTENT_ERROR_HANDLER_NAME = "mql$contentErrorHandler";

    private static final Map<Set<String>, Class<?>> VAR_HOLDERS = new ConcurrentHashMap<>();

    private final List<String> errors = new ArrayList<>(); // Accumulated as we go.

    private final Map<String, Map.Entry<Integer, Class<?>>> libraries = new HashMap<>();

    private static final AtomicInteger MODULE_COUNTER = new AtomicInteger(0);
    private final int moduleIndex = MODULE_COUNTER.getAndIncrement();
    private boolean frozen = false; // Set once lowering is started.

    private final Set<String> variables = new HashSet<>();
    private final List<Code> initializers = new ArrayList<>();
    private final List<Code> scripts = new ArrayList<>();

    record Code(@NotNull String debugName, @UnknownNullability Class<?> scriptInterface, @NotNull MqlExpr code,
                @NotNull List<String> locals, @NotNull Map<String, Map.Entry<Integer, Class<?>>> context) {
    }

    public MqlCodeBuilder() {
        libraries.put("math", Map.entry(-1, MqlMath.class));
        libraries.put("m", Map.entry(-1, MqlMath.class));
    }

    public int moduleIndex() {
        return moduleIndex;
    }

    public @NotNull List<String> errors() {
        return errors;
    }

    public void addLibrary(@NotNull Class<?> libraryClass, @NotNull String... names) {
        assertNotFrozen();
        for (var name : names) {
            libraries.put(name, Map.entry(-1, libraryClass));
        }
    }

    public void addInitializer(@NotNull String text, @Nullable String debugName) {
        assertNotFrozen();
        var code = parseText(text, false);
        if (code == null) return; // Error was accumulated

        var locals = collectVariables(code);


        var codeName = Objects.requireNonNullElseGet(debugName, () -> String.valueOf(text.hashCode()));
        this.initializers.add(new Code(codeName, null, code, locals, Map.copyOf(libraries)));
    }

    public void addScript(@NotNull Class<?> scriptInterface, @NotNull String text,
                          @Nullable String debugName, boolean isSimpleExpr) {
        assertNotFrozen();
        var code = parseText(text, isSimpleExpr);
        if (code == null) return; // Error was accumulated

        var locals = collectVariables(code);

        var contextObjects = new HashMap<>(libraries);

        var codeName = Objects.requireNonNullElseGet(debugName, () -> String.valueOf(text.hashCode()));
        this.scripts.add(new Code(codeName, scriptInterface, code, locals, Map.copyOf(libraries)));
    }

    public @NotNull MethodHandle varHolder() {
        try {
            var clazz = varHolderClass();
            return MethodHandles.lookup().in(clazz)
                    .findConstructor(clazz, MethodType.methodType(void.class, ContentError.Handler.class))
                    .asType(MethodType.methodType(Object.class, ContentError.Handler.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public @NotNull MethodHandle lowerInitializer() {
        try {
            var bytecode = lowerInitializerCode();
            var defined = MethodHandles.lookup().defineHiddenClass(bytecode, true);
            return defined.findStatic(defined.lookupClass(), "modInit",
                            MethodType.methodType(void.class, varHolderClass()))
                    .asType(MethodType.methodType(void.class, Object.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public @NotNull MethodHandle lowerScript(int index) {
        try {
            var bytecode = lowerScriptCode(index);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(scripts.get(0).scriptInterface, MethodHandles.lookup());
            var defined = lookup.defineHiddenClass(bytecode, true);
            return defined.findConstructor(defined.lookupClass(), MethodType.methodType(void.class, varHolderClass()))
                    .asType(MethodType.methodType(Object.class, Object.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public @NotNull Class<?> varHolderClass() {
        frozen = true;
        return getVarHolderClass(variables);
    }

    public byte @Nullable [] lowerInitializerCode() {
        var varHolderClass = varHolderClass();

        var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        var initClass = "net/hollowcube/mql/internal/Initializer";
        cw.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, initClass, null,
                toName(Object.class), null);

        {   // Add a static initializer method which takes the VarHolder instance as parameter.
            var mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "modInit",
                    methodDescriptor(void.class, varHolderClass), null, null);
            mv.visitCode();
            int localsStart = 1; // 0=varHolder

            // Invoke the initializers in order
            for (var initializer : initializers) {
                emitCodeBlock(mv, localsStart, null, toName(varHolderClass), initializer, null);
                mv.visitInsn(POP2); // Pop the result of the initializer
                // This does make kind of a dumb POP2 DCONST_0 POP2 RETURN sequence
                // but it doesnt really seem pressing to fix.
            }

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    public byte @Nullable [] lowerScriptCode(int index) {
        var varHolderClass = varHolderClass();
        if (scripts.size() <= index) return null;
        var script = scripts.get(index);

        var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        var scriptClass = script.scriptInterface.getPackageName().replace('.', '/') + "/Script" + index;
        cw.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, scriptClass, null,
                toName(Object.class), new String[]{toName(script.scriptInterface)});

        // Always have a vars field
        cw.visitField(ACC_PRIVATE | ACC_FINAL, VAR_HOLDER_FIELD_NAME,
                toDescriptor(varHolderClass), null, null).visitEnd();

        {   // Take the vars field as a constructor parameter
            var mv = cw.visitMethod(ACC_PUBLIC, "<init>",
                    methodDescriptor(void.class, varHolderClass), null, null);
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, toName(Object.class), "<init>",
                    methodDescriptor(void.class), false);

            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(PUTFIELD, scriptClass, VAR_HOLDER_FIELD_NAME, toDescriptor(varHolderClass));

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        {
            Method evalMethod = script.scriptInterface.getMethods()[0]; //TODO find it properly.
            var mv = cw.visitMethod(ACC_PUBLIC, evalMethod.getName(),
                    methodDescriptor(evalMethod.getReturnType(), evalMethod.getParameterTypes()),
                    null, null);
            mv.visitCode();

            int localsStart = 1 + evalMethod.getParameterCount(); // 0=this, 1..n=parameters
            var context = new HashMap<>(script.context);
            for (int i = 0; i < evalMethod.getParameterCount(); i++) {
                var env = evalMethod.getParameters()[i].getAnnotation(MqlEnv.class);
                if (env == null) throw new IllegalStateException("Missing @MqlEnv annotation on parameter " + i);
                for (var name : env.value()) {
                    context.put(name.toLowerCase(Locale.ROOT),
                            Map.entry(i + 1, evalMethod.getParameterTypes()[i]));
                }
            }
            emitCodeBlock(mv, localsStart, scriptClass, toName(varHolderClass), script, context);

            mv.visitInsn(DRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private @Nullable MqlExpr parseText(@NotNull String text, boolean isSimpleExpr) {
        try {
            return new MqlParser(text, isSimpleExpr).parse();
        } catch (MqlParseError e) {
            this.errors.add(e.getMessage());
            return null;
        }
    }

    /**
     * Collects all variables usages and locals, returning the locals. The variables set is updated with the
     * non-local variables found.
     */
    private @NotNull List<String> collectVariables(@NotNull MqlExpr expr) {
        // Look for references to variables and accumulate them in the variables set.
        var locals = new HashSet<String>();
        expr.visit(new VariableExtractionVisitor(variables, locals), null);
        return locals.isEmpty() ? List.of() : List.copyOf(locals);
    }

    private void emitCodeBlock(
            @NotNull MethodVisitor mv, int localsStart,
            @Nullable String scriptClass, @NotNull String varHandleClass,
            @NotNull Code code, @Nullable Map<String, Map.Entry<Integer, Class<?>>> context
    ) {
        final Label startLabel = new Label(), endLabel = new Label();

        // Create the local variables
        for (int i = 0; i < code.locals.size(); i++) {
            mv.visitLocalVariable(code.locals.get(i), "D", null,
                    startLabel, endLabel, localsStart + i);
        }

        mv.visitLabel(startLabel);

        // Evaluate the code
        new BytecodeGenerator(mv, localsStart, scriptClass, varHandleClass, Objects.requireNonNullElse(context, code.context))
                .visit(code.code, code.locals);

        mv.visitLabel(endLabel);
    }

    private static @NotNull Class<?> getVarHolderClass(@NotNull Set<String> variables) {
        return VAR_HOLDERS.computeIfAbsent(variables, vars -> {
            var className = "net/hollowcube/mql/internal/VarHolder$" + vars.hashCode();

            var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cw.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, className, null,
                    toName(Object.class), null);

            cw.visitField(ACC_PUBLIC | ACC_FINAL, CONTENT_ERROR_HANDLER_NAME,
                    toDescriptor(ContentError.Handler.class), null, null).visitEnd();

            for (var var : vars) {
                cw.visitField(ACC_PUBLIC, var, "D", null, null).visitEnd();
            }

            {   // Constructor with content error handler
                var mv = cw.visitMethod(ACC_PUBLIC, "<init>", methodDescriptor(void.class, ContentError.Handler.class), null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, toName(Object.class), "<init>", methodDescriptor(void.class), false);

                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitFieldInsn(PUTFIELD, className, CONTENT_ERROR_HANDLER_NAME, toDescriptor(ContentError.Handler.class));

                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            cw.visitEnd();

            try {
                return MethodHandles.lookup().defineClass(cw.toByteArray());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void assertNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("Cannot modify builder after lowering has started.");
        }
    }

}
