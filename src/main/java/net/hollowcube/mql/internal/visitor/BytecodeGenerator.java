package net.hollowcube.mql.internal.visitor;

import net.hollowcube.mql.internal.MqlRuntime;
import net.hollowcube.mql.internal.tree.*;
import net.hollowcube.mql.jit.AsmUtil;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

import static org.objectweb.asm.Opcodes.*;

public class BytecodeGenerator implements MqlVisitor<List<String>, Void> {
    private final MethodVisitor mv;

    public BytecodeGenerator(@NotNull MethodVisitor mv) {
        this.mv = mv;
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
                // if 0 use rhs, else use lhs
                throw new RuntimeException("Null coalesce operator not supported in JIT mode");
            }
            case GTE -> mv.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "gte", "(DD)D", false);
            case GE -> mv.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "ge", "(DD)D", false);
            case LTE -> mv.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "lte", "(DD)D", false);
            case LE -> mv.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "le", "(DD)D", false);
            case EQ -> mv.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "eq", "(DD)D", false);
            case NEQ -> mv.visitMethodInsn(INVOKESTATIC, AsmUtil.toName(MqlRuntime.class), "neq", "(DD)D", false);
        }

        return null;
    }

    @Override
    public Void visitAccessExpr(@NotNull MqlAccessExpr expr, List<String> strings) {
        return MqlVisitor.super.visitAccessExpr(expr, strings);
    }

    @Override
    public Void visitRefExpr(@NotNull MqlIdentExpr expr, List<String> strings) {
        return MqlVisitor.super.visitRefExpr(expr, strings);
    }

    @Override
    public Void visitArgListExpr(@NotNull MqlArgListExpr mqlArgListExpr, List<String> strings) {
        return MqlVisitor.super.visitArgListExpr(mqlArgListExpr, strings);
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
    public Void visitCallExpr(@NotNull MqlCallExpr expr, List<String> strings) {
        return MqlVisitor.super.visitCallExpr(expr, strings);
    }

    @Override
    public Void visitBlockExpr(@NotNull MqlBlockExpr expr, List<String> strings) {
        return MqlVisitor.super.visitBlockExpr(expr, strings);
    }

    @Override
    public Void visitIndexExpr(@NotNull MqlIndexExpr expr, List<String> strings) {
        return MqlVisitor.super.visitIndexExpr(expr, strings);
    }

    @Override
    public Void visitThisExpr(@NotNull MqlThisExpr expr, List<String> strings) {
        return MqlVisitor.super.visitThisExpr(expr, strings);
    }

    @Override
    public Void visitContinueExpr(@NotNull MqlContinueExpr expr, List<String> strings) {
        return MqlVisitor.super.visitContinueExpr(expr, strings);
    }

    @Override
    public Void visitBreakExpr(@NotNull MqlBreakExpr expr, List<String> strings) {
        return MqlVisitor.super.visitBreakExpr(expr, strings);
    }

    @Override
    public Void visitReturnExpr(@NotNull MqlReturnExpr expr, List<String> strings) {
        return MqlVisitor.super.visitReturnExpr(expr, strings);
    }
}
