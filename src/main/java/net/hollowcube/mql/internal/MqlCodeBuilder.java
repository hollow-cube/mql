package net.hollowcube.mql.internal;

import net.hollowcube.mql.builtin.MqlMath;
import net.hollowcube.mql.internal.tree.MqlExpr;
import net.hollowcube.mql.internal.visitor.BytecodeGenerator;
import net.hollowcube.mql.internal.visitor.VariableExtractionVisitor;
import net.hollowcube.mql.jit.AsmUtil;
import net.hollowcube.mql.parser.MqlParseError;
import net.hollowcube.mql.parser.MqlParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.*;

import static org.objectweb.asm.Opcodes.*;


public class MqlCodeBuilder {
    private static final String SCRIPT_PACKAGE_NAME = "net/hollowcube/mql";

    private final List<String> errors = new ArrayList<>(); // Accumulated as we go.

    private final Map<String, Class<?>> libraries = new HashMap<>();

    private final Set<String> variables = new HashSet<>();
    private final List<Code> initializers = new ArrayList<>();
    private final List<Code> scripts = new ArrayList<>();

    record Code(@NotNull String debugName, @UnknownNullability Class<?> scriptInterface, @NotNull MqlExpr code,
                @NotNull List<String> locals, @NotNull Map<String, Class<?>> contextObjects) {
    }

    public MqlCodeBuilder() {
        libraries.put("math", MqlMath.class);
        libraries.put("m", MqlMath.class);
    }

    public void addInitializer(@NotNull String text, @Nullable String debugName) {
        var code = parseText(text, false);
        if (code == null) return; // Error was accumulated

        var locals = collectVariables(code);

        var codeName = Objects.requireNonNullElseGet(debugName, () -> String.valueOf(text.hashCode()));
        this.initializers.add(new Code(codeName, null, code, new ArrayList<>(), Map.copyOf(libraries)));
    }

    public @Nullable Class<?> lower() {
        if (!errors.isEmpty()) return null;

        final ClassWriter moduleClass;
        if (scripts.size() == 1) {
            moduleClass = createSingletonClass(scripts.getFirst());
        } else {
            throw new UnsupportedOperationException("todo");
        }

        // Add the fields for any defined variables
        for (var variable : variables) {
            moduleClass.visitField(ACC_PUBLIC, variable, "D", null, null).visitEnd();
        }

        // Generate the constructor for the module class, as well as the empty constructor
        appendInitConstructor(moduleClass);

        return null; //todo

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

    // Implements the case where there is only one script so we only need one class.
    private @NotNull ClassWriter createSingletonClass(@NotNull Code script) {
        final String className = SCRIPT_PACKAGE_NAME + "/" + "Test123"; //todo generate a name from debugName.

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, className, null, AsmUtil.toName(Object.class),
                // Implement both the script interface and the module interface because this is a singleton.
                new String[]{AsmUtil.toName(script.scriptInterface), AsmUtil.toName(Module.class)});

        return cw;
    }

    private void appendInitConstructor(@NotNull ClassWriter moduleClass) {
        final MethodVisitor ctor = moduleClass.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, AsmUtil.toName(Object.class), "<init>", "()V", false);

        //todo init the script array

        for (var initializer : initializers) {
            emitCodeBlock(ctor, initializer);
        }

        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
    }

    private void emitCodeBlock(@NotNull MethodVisitor mv, @NotNull Code code) {
        final Label startLabel = new Label(), endLabel = new Label();
        mv.visitLabel(startLabel);

        // Create the local variables
        for (int i = 0; i < code.locals.size(); i++) {
            mv.visitLocalVariable(code.locals.get(i), "D", null,
                    // Always start at 1 because 0 is reserved for "this"
                    startLabel, endLabel, i + 1);
        }

        // Evaluate the code
        new BytecodeGenerator(mv, code.contextObjects).visit(code.code, code.locals);

        mv.visitLabel(endLabel);
    }

}
