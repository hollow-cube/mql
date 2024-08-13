package net.hollowcube.mql.tree;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record MqlArgListExpr(@NotNull List<MqlExpr> args) implements MqlExpr {

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitArgListExpr(this, p);
    }

    public int size() {
        return args().size();
    }
    
}
