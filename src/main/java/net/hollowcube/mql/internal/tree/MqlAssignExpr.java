package net.hollowcube.mql.internal.tree;

public record MqlAssignExpr(
        MqlAccessExpr target,
        MqlExpr rhs
) implements MqlExpr {
    @Override
    public <P, R> R visit(MqlVisitor<P, R> visitor, P p) {
        return visitor.visitAssignExpr(this, p);
    }
}
