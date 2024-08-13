package net.hollowcube.mql.tree;

import org.jetbrains.annotations.NotNull;

public final class MqlThisExpr implements MqlExpr {
    public static final MqlThisExpr INSTANCE = new MqlThisExpr();

    private MqlThisExpr() {
    }

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitThisExpr(this, p);
    }

}
