package net.hollowcube.mql.tree;

import org.jetbrains.annotations.NotNull;

public final class MqlContinueExpr implements MqlExpr {
    public static final MqlContinueExpr INSTANCE = new MqlContinueExpr();

    private MqlContinueExpr() {
    }

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitContinueExpr(this, p);
    }

}
