package net.hollowcube.mql.internal.tree;

import org.jetbrains.annotations.NotNull;

public record MqlCallExpr(@NotNull MqlExpr access, @NotNull MqlArgListExpr argList) implements MqlExpr {

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitCallExpr(this, p);
    }

}
