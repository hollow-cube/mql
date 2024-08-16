package net.hollowcube.mql.internal;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;


public class MqlCodeBuilder {
    private static final String SCRIPT_PACKAGE_NAME = "net/hollowcube/mql";

    private final List<String> errors = new ArrayList<>(); // Accumulated as we go.

    private final Set<String> variables = new HashSet<>();
    private final List<Code> initializers = new ArrayList<>();
    private final List<Code> scripts = new ArrayList<>();

    record Code(@NotNull String debugName, @UnknownNullability Class<?> scriptInterface,
                @NotNull MqlExpr code, @NotNull List<String> locals) {
    }

    public void addInitializer(@NotNull String text) {
        var code = parseText(text);
        if (code == null) return; // Error was accumulated

        collectVariables(code);
        this.initializers.add(new Code("todo", null, code, new ArrayList<>()));
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

    private @Nullable MqlExpr parseText(@NotNull String text) {
        try {
            return new MqlParser(text).parse();
        } catch (MqlParseError e) {
            this.errors.add(e.getMessage());
            return null;
        }
    }

    private void collectVariables(@NotNull MqlExpr expr) {
        // Look for references to variables and accumulate them in the variables set.
        expr.visit(VariableExtractionVisitor.INSTANCE, this.variables);
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
            mv.visitLocalVariable(code.locals.get(i), "D", null, startLabel, endLabel, i);
        }

        // Evaluate the code
        new BytecodeGenerator(mv).visit(code.code, code.locals);

        mv.visitLabel(endLabel);
    }

}
