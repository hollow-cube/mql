package net.hollowcube.mql.internal.tree;

import org.jetbrains.annotations.NotNull;

public final class MqlBreakExpr implements MqlExpr {
    public static final MqlBreakExpr INSTANCE = new MqlBreakExpr();

    private MqlBreakExpr() {
    }

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitBreakExpr(this, p);
    }

}
