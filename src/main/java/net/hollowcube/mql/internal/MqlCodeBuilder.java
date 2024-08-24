package net.hollowcube.mql.internal;

import net.hollowcube.mql.ContentError;
import net.hollowcube.mql.MqlCompiler;
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
import org.objectweb.asm.Opcodes;

import java.util.*;

import static net.hollowcube.mql.jit.AsmUtil.*;
import static org.objectweb.asm.Opcodes.*;


public class MqlCodeBuilder {
    private static final String SCRIPT_PACKAGE_NAME = "net/hollowcube/mql";

    public static final String SCRIPTS_FIELD_NAME = "mql$scripts";
    public static final String CONTENT_ERROR_HANDLER_FIELD_NAME = "mql$contentErrorHandler";

    private final List<String> errors = new ArrayList<>(); // Accumulated as we go.

    private final Map<String, Class<?>> libraries = new HashMap<>();

    private final int moduleIndex = 2; //todo

    private final Set<String> variables = new HashSet<>();
    private final List<Code> initializers = new ArrayList<>();
    private final List<Code> scripts = new ArrayList<>();

    private String moduleClassName;

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
        this.initializers.add(new Code(codeName, null, code, locals, Map.copyOf(libraries)));
    }

    public byte @Nullable [] lowerCode() {
        if (!errors.isEmpty()) return null;

        moduleClassName = SCRIPT_PACKAGE_NAME + "/" + "Test123"; //todo generate a name from debugName.
        var moduleClass = createModuleClass();

        // Add the fields for any defined variables
        for (var variable : variables) {
            moduleClass.visitField(ACC_PUBLIC, variable, "D", null, null).visitEnd();
        }

        // Generate the constructor for the module class, as well as the empty constructor
        appendInitConstructor(moduleClass);

        // Implement the MqlModule.Instance interface
        appendModuleGetScript(moduleClass);

        // Generate the inner classes
        for (int i = 0; i < scripts.size(); i++) {
//            var scriptClass = createScriptClass();
        }

        return moduleClass.toByteArray();
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

    private @NotNull ClassWriter createModuleClass() {
        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, moduleClassName, null,
                AsmUtil.toName(Object.class), new String[]{AsmUtil.toName(Module.class)});
        cw.visitOuterClass(moduleClassName, null, null);

        // Add `mql$scripts` synthetic field.
        cw.visitField(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, SCRIPTS_FIELD_NAME,
                toDescriptor(Object[].class), null, null).visitEnd();

        // Add content handler init field
        cw.visitField(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, CONTENT_ERROR_HANDLER_FIELD_NAME,
                toDescriptor(ContentError.Handler.class), null, null).visitEnd();

        return cw;
    }

    private void createScriptClass(@NotNull ClassWriter cw, @NotNull String scriptClassName, @NotNull Code code) {
        cw.visit(V21, ACC_PUBLIC, scriptClassName, null, "java/lang/Object",
                new String[]{AsmUtil.toName(code.scriptInterface)});
        cw.visitInnerClass(scriptClassName, moduleClassName,
                scriptClassName.substring(scriptClassName.indexOf("$")), ACC_PUBLIC);

        //        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(LOuterClass;)V", null, null);
//        mv.visitCode();
//
//        // Constructor logic (just call super for demonstration)
//        mv.visitVarInsn(Opcodes.ALOAD, 0);
//        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
//        mv.visitInsn(Opcodes.RETURN);
//        mv.visitMaxs(1, 2);
//        mv.visitEnd();

        cw.visitEnd();


        //
//        // Define the inner class
//        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "OuterClass$InnerClass", null, "java/lang/Object", null);
//
//        // Add the InnerClasses attribute
//        cw.visitInnerClass("OuterClass$InnerClass", "OuterClass", "InnerClass", Opcodes.ACC_PUBLIC);
//
//        // Generate a constructor for the inner class

//
//        // Finish the inner class
//        cw.visitEnd();
//
//        // Finish the outer class
//        cw.visitEnd();


//        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
//        cw.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, moduleClassName, null,
//                AsmUtil.toName(Object.class), new String[]{AsmUtil.toName(Module.class)});
//
//        // Add `mql$scripts` synthetic field.
//        cw.visitField(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, SCRIPTS_FIELD_NAME,
//                toDescriptor(Object[].class), null, null).visitEnd();
//
//        // Add content handler init field
//        cw.visitField(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, CONTENT_ERROR_HANDLER_FIELD_NAME,
//                toDescriptor(ContentError.Handler.class), null, null).visitEnd();

    }

    private void appendInitConstructor(@NotNull ClassWriter moduleClass) {
        final MethodVisitor ctor = moduleClass.visitMethod(ACC_PUBLIC, "<init>",
                methodDescriptor(void.class, ContentError.Handler.class), null, null);
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, AsmUtil.toName(Object.class), "<init>",
                methodDescriptor(void.class), false);

        // Initialize the scripts array
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitLdcInsn(scripts.size());
        ctor.visitTypeInsn(ANEWARRAY, AsmUtil.toName(Object.class));
        for (int i = 0; i < scripts.size(); i++) {
            var script = scripts.get(i);
            var scriptClassName = SCRIPT_PACKAGE_NAME + "/" + "Test123$Script" + i; //todo generate a name from debugName.

            ctor.visitInsn(DUP); // Duplicate the array reference
            ctor.visitLdcInsn(i); // Load the index

            ctor.visitTypeInsn(NEW, scriptClassName); // Instantiate class
            ctor.visitInsn(DUP);
            ctor.visitVarInsn(ALOAD, 0); // Load the `this` reference for inner class
            ctor.visitMethodInsn(INVOKESPECIAL, scriptClassName, "<init>",
                    "(L" + moduleClassName + ";)V", false);

            ctor.visitInsn(AASTORE); // Store in scripts array
        }
        ctor.visitFieldInsn(PUTFIELD, moduleClassName, SCRIPTS_FIELD_NAME, toDescriptor(Object[].class));

        // Store the content error handler
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitVarInsn(ALOAD, 1);
        ctor.visitFieldInsn(PUTFIELD, moduleClassName, CONTENT_ERROR_HANDLER_FIELD_NAME,
                toDescriptor(ContentError.Handler.class));

        // Invoke the initializers in order
        for (var initializer : initializers) {
            emitCodeBlock(ctor, initializer);
        }

        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
    }

    private void appendModuleGetScript(@NotNull ClassWriter moduleClass) {
        final MethodVisitor mv = moduleClass.visitMethod(
                Opcodes.ACC_PUBLIC,
                "getScript",
                methodDescriptor(Object.class, MqlCompiler.Unit.class),
                "<T:Ljava/lang/Object;>(L" + toName(MqlCompiler.Unit.class) + "<TT;>;)TT;",
                null
        );

        mv.visitCode();

        mv.visitLdcInsn(moduleIndex);
        mv.visitVarInsn(ALOAD, 0); // `this` parameter
        mv.visitFieldInsn(GETFIELD, moduleClassName, SCRIPTS_FIELD_NAME, toDescriptor(Object[].class));
        mv.visitVarInsn(ALOAD, 1); // `ref` parameter

        mv.visitMethodInsn(
                INVOKESTATIC,
                toName(MqlRuntime.class),
                "getScript",
                methodDescriptor(Object.class, int.class, Object[].class, MqlCompiler.Unit.class),
                false
        );

        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
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
        new BytecodeGenerator(moduleClassName, mv, code.contextObjects)
                .visit(code.code, code.locals);

        mv.visitLabel(endLabel);
    }

}
