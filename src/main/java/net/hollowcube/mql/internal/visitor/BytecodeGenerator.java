package net.hollowcube.mql.internal.visitor;

import net.hollowcube.mql.internal.ForeignFunction;
import net.hollowcube.mql.internal.tree.*;
import net.hollowcube.mql.jit.AsmUtil;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.hollowcube.mql.internal.visitor.VariableExtractionVisitor.LOCAL_NAMES;
import static org.objectweb.asm.Opcodes.*;

public class BytecodeGenerator implements MqlVisitor<List<String>, Void> {
    private final MethodVisitor mv;

    private final Map<String, Class<?>> contextObjects;

    public BytecodeGenerator(@NotNull MethodVisitor mv, @NotNull Map<String, Class<?>> contextObjects) {
        this.mv = mv;

        this.contextObjects = contextObjects;
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
        // Visit both sides, resulting in two doubles on the stack
        visit(expr.lhs(), locals);
        visit(expr.rhs(), locals);

        // Perform the operation
        switch (expr.operator()) {
            case PLUS -> mv.visitInsn(DADD);
            case MINUS -> mv.visitInsn(DSUB);
            case MUL -> mv.visitInsn(DMUL);
            case DIV -> mv.visitInsn(DDIV);
            case NULL_COALESCE -> {
                /*
                This behaves kinda weirdly in Molang, here is apparently the rules:

                * ?? applies to the following:
                    has not yet been initialized
                    is a reference to a deleted entity
                    is an invalid reference
                    holds an error
                Note that this requires we handle variables that have not been initialized. I think we can just use NaN or Inf
                 */
                throw new RuntimeException("Null coalesce operator not supported in JIT mode");
            }
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
            mv.visitVarInsn(DLOAD, localIndex + 1);
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
                throw new UnsupportedOperationException("invalid local (" + ident.value() + ") in assignment");
            }

            // Add 1 because 0 is always reserved for `this`.
            mv.visitVarInsn(DSTORE, localIndex + 1);
        } else {
            throw new UnsupportedOperationException("invalid assignment target");
        }

        return null;
    }

    @Override
    public Void visitCallExpr(@NotNull MqlCallExpr expr, List<String> strings) {
        if (!(expr.target() instanceof MqlAccessExpr access))
            throw new UnsupportedOperationException("Invalid call target");
        if (!(access.lhs() instanceof MqlIdentExpr ident))
            throw new UnsupportedOperationException("Structs are not supported.");
        visitAnyCall(ident.value(), access.target(), expr.argList().args());
        return null;
    }

    @Override
    public Void visitBlockExpr(@NotNull MqlBlockExpr expr, List<String> strings) {
        return MqlVisitor.super.visitBlockExpr(expr, strings);
    }

    private void visitAnyCall(@NotNull String object, @NotNull String method, @NotNull List<MqlExpr> args) {
        var contextObject = contextObjects.get(object.toLowerCase(Locale.ROOT)); // Molang is case-insensitive
        if (contextObject == null) throw new UnsupportedOperationException("object not found " + object);

        var function = ForeignFunction.lookup(contextObject, method);
        if (function == null) {
            var msg = String.format("method not found %s.%s#%d", object, method, args.size());
            throw new UnsupportedOperationException(msg);
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
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Void defaultValue() {
        throw new UnsupportedOperationException("not implemented");
    }
}
