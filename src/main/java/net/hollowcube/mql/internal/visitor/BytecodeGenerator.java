package net.hollowcube.mql.internal.visitor;

import net.hollowcube.mql.ContentError;
import net.hollowcube.mql.foreign.ContentErrorException;
import net.hollowcube.mql.internal.AsmUtil;
import net.hollowcube.mql.internal.ForeignFunction;
import net.hollowcube.mql.internal.MqlCodeBuilder;
import net.hollowcube.mql.internal.tree.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.hollowcube.mql.internal.AsmUtil.*;
import static net.hollowcube.mql.internal.MqlCodeBuilder.CONTENT_ERROR_HANDLER_NAME;
import static net.hollowcube.mql.internal.visitor.VariableExtractionVisitor.LOCAL_NAMES;
import static net.hollowcube.mql.internal.visitor.VariableExtractionVisitor.VARIABLE_NAMES;
import static org.objectweb.asm.Opcodes.*;

public class BytecodeGenerator implements MqlVisitor<List<String>, Void> {
    private final MethodVisitor mv;

    private final String varHolderClass;
    // Present when writing a script class, not when writing an initializer function
    // Controls how the var holder reference is fetched (script = field, init = local)
    private final @Nullable String scriptClass;

    private final int localsStart;

    private final Map<String, Map.Entry<Integer, Class<?>>> context;

    // Holds writers for the null coalesce right hand expressions which are
    // written only if a null coalesce operator is encountered at the same
    // depth for which it was defined.
    private final Map<Integer, Runnable> coalesceValues = new HashMap<>();
    private int depthIndex = 0;

    public BytecodeGenerator(
            @NotNull MethodVisitor mv,
            int localsStart,
            @Nullable String scriptClass,
            @NotNull String varHolderClass,
            @NotNull Map<String, Map.Entry<Integer, Class<?>>> context
    ) {
        this.mv = mv;
        this.localsStart = localsStart;

        this.varHolderClass = varHolderClass;
        this.scriptClass = scriptClass;

        this.context = context;
    }

    @Override
    public Void visit(@NotNull MqlExpr expr, List<String> strings) {
        depthIndex++;
        MqlVisitor.super.visit(expr, strings);
        depthIndex--;
        return null;
    }

    @Override
    public Void visitNumberExpr(@NotNull MqlNumberExpr expr, @NotNull List<String> locals) {
        double value = expr.value();
        if (value == 0) {
            mv.visitInsn(DCONST_0);
        } else if (value == 1) {
            mv.visitInsn(DCONST_1);
        } else {
            mv.visitLdcInsn(value);
        }
        return null;
    }

    @Override
    public Void visitStringExpr(MqlStringExpr expr, List<String> strings) {
        throw new UnsupportedOperationException("strings are not implemented");
    }

    @Override
    public Void visitUnaryExpr(@NotNull MqlUnaryExpr expr, List<String> strings) {
        visit(expr.rhs(), null);

        //noinspection SwitchStatementWithTooFewBranches
        switch (expr.operator()) {
            case NEGATE -> mv.visitInsn(DNEG);
        }
        return null;
    }

    @Override
    public Void visitBinaryExpr(@NotNull MqlBinaryExpr expr, @NotNull List<String> locals) {
        // Null coalesce is a special case that needs to be handled separately.
        if (expr.operator() == MqlBinaryExpr.Op.NULL_COALESCE) {
            visitNullCoalesce(expr.lhs(), expr.rhs(), locals);
            return null;
        }

        // Visit both sides, resulting in two doubles on the stack
        visit(expr.lhs(), locals);
        visit(expr.rhs(), locals);

        // Perform the operation
        switch (expr.operator()) {
            case PLUS -> mv.visitInsn(DADD);
            case MINUS -> mv.visitInsn(DSUB);
            case MUL -> mv.visitInsn(DMUL);
            case DIV -> visitDivision();
            case GTE, GE, LTE, LE, EQ, NEQ -> {
                // We need to compare the two values and push a boolean result.
                // We can't use the normal comparison instructions because they don't work with NaN.
                // Instead, we'll use the DCMPL instruction, which pushes -1, 0, or 1 onto the stack.
                // We can then compare that to 0 to get the result.
                mv.visitInsn(DCMPL);
                var trueCase = new Label();
                switch (expr.operator()) {
                    case GTE -> mv.visitJumpInsn(IFGE, trueCase);
                    case GE -> mv.visitJumpInsn(IFGT, trueCase);
                    case LTE -> mv.visitJumpInsn(IFLE, trueCase);
                    case LE -> mv.visitJumpInsn(IFLT, trueCase);
                    case EQ -> mv.visitJumpInsn(IFEQ, trueCase);
                    case NEQ -> mv.visitJumpInsn(IFNE, trueCase);
                }
                mv.visitInsn(DCONST_0);
                Label end = new Label();
                mv.visitJumpInsn(GOTO, end);
                mv.visitLabel(trueCase);
                mv.visitInsn(DCONST_1);
                mv.visitLabel(end);
            }
        }

        return null;
    }

    private void visitDivision() {
        // Divide is a little tricky. We need to check for division by zero and generate a content error.

        // Duplicate the top value on the stack so we can compare it to zero.
        mv.visitInsn(DUP);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DCMPL);
        Label notZero = new Label(), end = new Label();
        mv.visitJumpInsn(IFNE, notZero);

        // If the divisor is zero, it is a content error.
        mv.visitInsn(POP2); // Get rid of LHS and RHS (both are category 2 types)
        mv.visitInsn(POP2);
        emitContentErrorOrCoalesce("Division by zero"); // Leaves 0 on stack
        mv.visitJumpInsn(GOTO, end);

        // If the divisor is not zero, we can safely divide.
        mv.visitLabel(notZero);
        mv.visitInsn(DDIV);
        mv.visitLabel(end);
    }

    private void visitNullCoalesce(@NotNull MqlExpr lhs, @NotNull MqlExpr rhs, @NotNull List<String> locals) {
        // Null Coalesce operators can apply to any of the following, converting
        // it to the right-hand side expression:
        //  * has not yet been initialized
        //  * is a reference to a deleted entity
        //  * is an invalid reference
        //  * holds an error
        // (though we dont currently support entities, so its really the first and last cases)
        // Referencing an uninitialized variable always generates a content error, so this is even
        // more generic to say that it just coalesces a content error into the RHS.

        // Set up the default value supplier for the next expression (the LHS)
        coalesceValues.put(depthIndex + 1, () -> visit(rhs, locals));

        // Evaluate the LHS which can handle a content error using the above supplier.
        visit(lhs, locals);

        // Clean up
        coalesceValues.remove(depthIndex + 1);
    }

    @Override
    public Void visitTernaryExpr(@NotNull MqlTernaryExpr expr, List<String> strings) {
        final Label falseJump = new Label(), endJump = new Label();

        visit(expr.condition(), strings);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DCMPL);
        mv.visitJumpInsn(IFEQ, falseJump); // if equal to zero 0, jump to false case

        visit(expr.trueCase(), strings);
        mv.visitJumpInsn(GOTO, endJump);

        mv.visitLabel(falseJump);
        if (expr.falseCase() == null) {
            // If we have no false case, just push 0.
            mv.visitInsn(DCONST_0);
        } else {
            visit(expr.falseCase(), strings);
        }

        // Jump to whatever the next expression will be.
        mv.visitLabel(endJump);
        return null;
    }

    @Override
    public Void visitAccessExpr(@NotNull MqlAccessExpr expr, List<String> strings) {
        // Note that non-variable accesses are always treated as a no-args call. Other exprs
        // containing a reference will never actually visit the access expression itself. This
        // may need to change when supporting structs (aka nested access).
        if (!(expr.lhs() instanceof MqlIdentExpr ident))
            throw new UnsupportedOperationException("Structs are not supported.");

        if (LOCAL_NAMES.contains(ident.value())) {
            int localIndex = strings.indexOf(expr.target());
            if (localIndex == -1) {
                throw new UnsupportedOperationException("invalid local (" + ident.value() + ") in access");
            }

            // Add 1 because 0 is always reserved for `this`.
            mv.visitVarInsn(DLOAD, localsStart + localIndex);
            return null;
        } else if (VARIABLE_NAMES.contains(ident.value())) {
            pushVarHolderRef();
            mv.visitFieldInsn(GETFIELD, varHolderClass, expr.target(), "D");
            return null;
        }

        visitAnyCall(ident.value(), expr.target(), List.of());
        return null;
    }

    @Override
    public Void visitAssignExpr(MqlAssignExpr expr, List<String> locals) {
        // Note: It is important that we never actually visit the underlying target expression.
        // When visiting an access expression directly, we treat it as a no-args call.
        if (!(expr.target().lhs() instanceof MqlIdentExpr ident))
            throw new UnsupportedOperationException("Structs are not supported.");

        visit(expr.rhs(), locals);
        // Value to be assigned is now at the top of the stack

        if (LOCAL_NAMES.contains(ident.value())) {
            int localIndex = locals.indexOf(expr.target().target());
            if (localIndex == -1) {
                throw new UnsupportedOperationException("invalid local (" + expr.target().target() + ") in assignment");
            }

            // Copy the value we are storing because it also is the result value of this expr.
            mv.visitInsn(DUP2);

            // Add 1 because 0 is always reserved for `this`.
            mv.visitVarInsn(DSTORE, localsStart + localIndex);
        } else if (VARIABLE_NAMES.contains(ident.value())) {
            mv.visitInsn(DUP2); // Keep another copy of the value for the result of the expression

            pushVarHolderRef();

            // There is sort of a tricky swap required here. The stack currently looks something like this:
            // { double, double_2nd, 'vars ref' }
            // But we need to swap the double and the vars ref. There is no particularly good vm instruction
            // for this operation, so we do the following:
            // DUP_X2 which clones the vars ref and moves it before the double resulting in the following:
            // { 'vars ref', double, double_2nd, 'vars ref' }
            // This is good except for that extra vars ref, so we just pop that.

            mv.visitInsn(DUP_X2);
            mv.visitInsn(POP); // Extraneous vars ref

            mv.visitFieldInsn(PUTFIELD, varHolderClass, expr.target().target(), "D");

            // The duplicated double is left on the stack for later.
        } else {
            throw new UnsupportedOperationException("invalid assignment target");
        }

        return null;
    }

    private void pushVarHolderRef() {
        if (scriptClass == null) {
            mv.visitVarInsn(ALOAD, 0); // Field ref
            return;
        }

        // Read from the vars field in the script class
        mv.visitVarInsn(ALOAD, 0); // this
        mv.visitFieldInsn(GETFIELD, scriptClass, MqlCodeBuilder.VAR_HOLDER_FIELD_NAME,
                "L" + varHolderClass + ";");
    }

    @Override
    public Void visitCallExpr(@NotNull MqlCallExpr expr, List<String> strings) {
        if (!(expr.target() instanceof MqlAccessExpr access))
            throw new UnsupportedOperationException("Invalid call target: " + expr.target());
        if (!(access.lhs() instanceof MqlIdentExpr ident))
            throw new UnsupportedOperationException("Structs are not supported.");
        visitAnyCall(ident.value(), access.target(), expr.argList().args());
        return null;
    }

    @Override
    public Void visitBlockExpr(@NotNull MqlBlockExpr expr, List<String> strings) {
        for (var child : expr.exprs()) {
            visit(child, strings);
            mv.visitInsn(POP2); // Pop the result of the expression
        }
        mv.visitInsn(DCONST_0); // Blocks always evaluate to zero
        return null;
    }

    private void visitAnyCall(@NotNull String object, @NotNull String method, @NotNull List<MqlExpr> args) {
        var contextObject = context.get(object.toLowerCase(Locale.ROOT)); // Molang is case-insensitive
        if (contextObject == null) throw new UnsupportedOperationException("object not found " + object);

        var function = ForeignFunction.lookup(contextObject.getValue(), method);
        if (function == null) {
            var msg = String.format("method not found %s.%s#%d", object, method, args.size());
            throw new UnsupportedOperationException(msg);
        }

        // Load the context object if it is not static
        if (!function.isStatic()) {
            if (contextObject.getKey() == -1)
                throw new UnsupportedOperationException("libraries must only have static functions");
            mv.visitVarInsn(ALOAD, contextObject.getKey());
        }

        // Validate then push all arguments on the stack, and convert them to the appropriate java type.
        if (function.paramTypes().length != args.size()) {
            var msg = String.format("argument count mismatch for %s.%s: %d != %d", object, method, function.paramTypes().length, args.size());
            throw new UnsupportedOperationException(msg);
        }
        for (int i = 0; i < args.size(); i++) {
            visit(args.get(i), null);
            //todo do type checking & correct conversion
            AsmUtil.convert(function.paramTypes()[i], double.class, mv);
        }

        // Setup try catch if the function is a content error source.
        Label tryStart = new Label(), tryEnd = new Label(), catchStart = new Label(), catchEnd = new Label();
        if (function.isContentErrorSource()) {
            mv.visitTryCatchBlock(tryStart, tryEnd, catchStart, toName(ContentErrorException.class));
            mv.visitLabel(tryStart);
        }

        // Call the function
        if (function.isStatic()) {
            mv.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(contextObject.getValue()), function.javaName(), methodDescriptor(function.returnType(), function.paramTypes()), false);
        } else {
            mv.visitMethodInsn(INVOKEVIRTUAL, AsmUtil.toName(contextObject.getValue()), function.javaName(), methodDescriptor(function.returnType(), function.paramTypes()), false);
        }

        // Handle error if the function is a content error source
        if (function.isContentErrorSource()) {
            mv.visitJumpInsn(GOTO, catchEnd); // We succeeded the call, skip the catch

            mv.visitLabel(tryEnd);
            mv.visitLabel(catchStart);
            // The exception is on the top of the stack. Get the message.
            emitContentErrorOrCoalesce(() -> {
                // This will consume the exception from the stack.
                mv.visitMethodInsn(INVOKEVIRTUAL, toName(ContentErrorException.class), "getMessage", methodDescriptor(String.class), false);
            }, () -> {
                // Otherwise if we didn't need the message, pop the exception.
                mv.visitInsn(POP);
            });

            mv.visitLabel(catchEnd);
        }
    }

    @Override
    public Void visitIndexExpr(@NotNull MqlIndexExpr expr, List<String> strings) {
        throw new UnsupportedOperationException("arrays are not implemented");
    }

    @Override
    public Void visitThisExpr(@NotNull MqlThisExpr expr, List<String> strings) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Void visitContinueExpr(@NotNull MqlContinueExpr expr, List<String> strings) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Void visitBreakExpr(@NotNull MqlBreakExpr expr, List<String> strings) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Void visitReturnExpr(@NotNull MqlReturnExpr expr, List<String> strings) {
        if (expr.value() != null) {
            visit(expr.value(), strings);
        } else {
            mv.visitInsn(DCONST_0);
        }

        mv.visitInsn(DRETURN);
        return null;
    }

    private void emitContentErrorOrCoalesce(@NotNull String constMessage) {
        emitContentErrorOrCoalesce(() -> mv.visitLdcInsn(constMessage), null);
    }

    private void emitContentErrorOrCoalesce(@NotNull Runnable pushMessage, @Nullable Runnable popMessage) {
        // If there is a waiting coalesce operator, run it and dont emit the error.
        var coalesce = coalesceValues.get(depthIndex);
        if (coalesce != null) {
            if (popMessage != null) popMessage.run();
            coalesce.run();
            return;
        }

        // Get the handler field (not null so no check needed)
        pushVarHolderRef(); // Var holder contains the content error handler
        mv.visitFieldInsn(Opcodes.GETFIELD, varHolderClass, CONTENT_ERROR_HANDLER_NAME,
                toDescriptor(ContentError.Handler.class));

        // new ContentError(0, constMessage)
        mv.visitTypeInsn(NEW, toName(ContentError.class));
        mv.visitInsn(DUP); // One for constructor, one for field
        mv.visitInsn(ICONST_0); //todo pass a real script index
        pushMessage.run();
        mv.visitMethodInsn(INVOKESPECIAL, toName(ContentError.class), "<init>",
                methodDescriptor(void.class, int.class, ContentError.class), false);

        // contentErrorHandler.handle(contentError)
        mv.visitMethodInsn(INVOKEINTERFACE, toName(ContentError.Handler.class),
                "handle", methodDescriptor(void.class, ContentError.class), true);

        // All content errors evaluate to zero.
        mv.visitInsn(DCONST_0);
    }

    @Override
    public Void defaultValue() {
        throw new UnsupportedOperationException("not implemented");
    }
}
