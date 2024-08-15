package net.hollowcube.mql.internal.tree;

import org.jetbrains.annotations.NotNull;

public record MqlBinaryExpr(
        @NotNull Op operator,
        @NotNull MqlExpr lhs,
        @NotNull MqlExpr rhs
) implements MqlExpr {
    public enum Op {
        PLUS,
        MINUS,
        DIV,
        MUL,
        NULL_COALESCE,
        GTE,
        GE,
        LTE,
        LE,
        EQ,
        NEQ
    }

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitBinaryExpr(this, p);
    }

}
