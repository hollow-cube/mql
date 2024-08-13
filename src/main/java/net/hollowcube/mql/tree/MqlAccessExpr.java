package net.hollowcube.mql.tree;

import org.jetbrains.annotations.NotNull;

public record MqlAccessExpr(@NotNull MqlExpr lhs, String target) implements MqlExpr {

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitAccessExpr(this, p);
    }
    
}
