package net.hollowcube.mql.internal.tree;

import org.jetbrains.annotations.NotNull;

// @formatter:off
public interface MqlVisitor<P, R> {

    default R visitBinaryExpr(@NotNull MqlBinaryExpr expr, P p) {
        visit(expr.lhs(), p);
        visit(expr.rhs(), p);
        return defaultValue();
    }

    default R visitUnaryExpr(@NotNull MqlUnaryExpr expr, P p) {
        visit(expr.rhs(), p);
        return defaultValue();
    }

    default R visitAccessExpr(@NotNull MqlAccessExpr expr, P p) {
        visit(expr.lhs(), p);
        return defaultValue();
    }

    default R visitNumberExpr(@NotNull MqlNumberExpr expr, P p) {
        return defaultValue();
    }

    default R visitRefExpr(@NotNull MqlIdentExpr expr, P p) {
        return defaultValue();
    }

    default R visitArgListExpr(@NotNull MqlArgListExpr mqlArgListExpr, P p) {
        for (var arg : mqlArgListExpr.args()) {
            visit(arg, p);
        }
        return defaultValue();
    }

    default R visitTernaryExpr(@NotNull MqlTernaryExpr expr, P p) {
        visit(expr.condition(), p);
        visit(expr.trueCase(), p);
        if (expr.falseCase() != null) visit(expr.falseCase(), p);
        return defaultValue();
    }

    default R visitCallExpr(@NotNull MqlCallExpr expr, P p) {
        visit(expr.target(), p);
        visit(expr.argList(), p);
        return defaultValue();
    }

    default R visitBlockExpr(@NotNull MqlBlockExpr expr, P p) {
        //todo!
        return defaultValue();
    }

    default R visitIndexExpr(@NotNull MqlIndexExpr expr, P p) {
        visit(expr.lhs(), p);
        visit(expr.target(), p);
        return defaultValue();
    }

    default R visitThisExpr(@NotNull MqlThisExpr expr, P p) {
        return defaultValue();
    }

    default R visitContinueExpr(@NotNull MqlContinueExpr expr, P p) {
        return defaultValue();
    }

    default R visitBreakExpr(@NotNull MqlBreakExpr expr, P p) {
        return defaultValue();
    }

    default R visitReturnExpr(@NotNull MqlReturnExpr expr, P p) {
        if (expr.value() != null) visit(expr.value(), p);
        return defaultValue();
    }

    default R visit(@NotNull MqlExpr expr, P p) {
        return expr.visit(this, p);
    }

    default R defaultValue() {
        return null;
    }
}
// @formatter:on
