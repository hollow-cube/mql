package net.hollowcube.mql.internal;

import net.hollowcube.mql.internal.tree.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TypeCache {
    private static final TypeCheckingVisitor VISITOR = new TypeCheckingVisitor();

    // We map expr trees to their types. Once a tree has been computed it is final.
    private final Map<MqlExpr, MqlType> cache = new HashMap<>();

    public @NotNull MqlType getType(@NotNull MqlExpr expr) {
        return cache.computeIfAbsent(expr, e -> e.visit(VISITOR, this));
    }

    // Note: There is a slightly odd back and forth here going on here with the cache
    // and the visitor. Whenever we request a type using getType it returns the cached
    // value or calls MqlExpr#visit. Note that it calls visit on the node directly
    // which sends it to visit___Expr NOT to `visit`.
    // In the visitor impls we use `visit` to call the next node in the tree, which
    // directs it to the cache again to avoid recomputing large trees.

    private static class TypeCheckingVisitor implements MqlVisitor<TypeCache, MqlType> {

        @Override
        public MqlType visit(@NotNull MqlExpr expr, TypeCache typeCache) {
            // WARNING: SEE ABOVE COMMENT ABOUT WHY THIS WORKS
            return typeCache.getType(expr);
            // WARNING: SEE ABOVE COMMENT ABOUT WHY THIS WORKS
        }

        @Override
        public MqlType visitNumberExpr(@NotNull MqlNumberExpr expr, TypeCache typeCache) {
            return MqlType.NUMBER;
        }

        @Override
        public MqlType visitStringExpr(MqlStringExpr expr, TypeCache typeCache) {
            return MqlType.STRING;
        }

        @Override
        public MqlType visitBinaryExpr(@NotNull MqlBinaryExpr expr, TypeCache typeCache) {
            return MqlVisitor.super.visitBinaryExpr(expr, typeCache);
        }

        @Override
        public MqlType visitUnaryExpr(@NotNull MqlUnaryExpr expr, TypeCache typeCache) {
            var rhsType = visit(expr.rhs(), typeCache);
            return switch (expr.operator()) {
                case NEGATE -> {
                    assertType(MqlType.NUMBER, rhsType);
                    yield MqlType.NUMBER;
                }
            };
        }

        @Override
        public MqlType visitAccessExpr(@NotNull MqlAccessExpr expr, TypeCache typeCache) {
            return MqlVisitor.super.visitAccessExpr(expr, typeCache);
        }

        @Override
        public MqlType visitAssignExpr(MqlAssignExpr expr, TypeCache typeCache) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public MqlType visitRefExpr(@NotNull MqlIdentExpr expr, TypeCache typeCache) {
            return MqlVisitor.super.visitRefExpr(expr, typeCache);
        }

        @Override
        public MqlType visitArgListExpr(@NotNull MqlArgListExpr mqlArgListExpr, TypeCache typeCache) {
            return MqlVisitor.super.visitArgListExpr(mqlArgListExpr, typeCache);
        }

        @Override
        public MqlType visitTernaryExpr(@NotNull MqlTernaryExpr expr, TypeCache typeCache) {
            return MqlVisitor.super.visitTernaryExpr(expr, typeCache);
        }

        @Override
        public MqlType visitCallExpr(@NotNull MqlCallExpr expr, TypeCache typeCache) {
            return MqlVisitor.super.visitCallExpr(expr, typeCache);
        }

        @Override
        public MqlType visitBlockExpr(@NotNull MqlBlockExpr expr, TypeCache typeCache) {
            return MqlVisitor.super.visitBlockExpr(expr, typeCache);
        }

        @Override
        public MqlType visitIndexExpr(@NotNull MqlIndexExpr expr, TypeCache typeCache) {
            return MqlVisitor.super.visitIndexExpr(expr, typeCache);
        }

        @Override
        public MqlType visitThisExpr(@NotNull MqlThisExpr expr, TypeCache typeCache) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public MqlType visitContinueExpr(@NotNull MqlContinueExpr expr, TypeCache typeCache) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public MqlType visitBreakExpr(@NotNull MqlBreakExpr expr, TypeCache typeCache) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public MqlType visitReturnExpr(@NotNull MqlReturnExpr expr, TypeCache typeCache) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public MqlType defaultValue() {
            throw new UnsupportedOperationException();
        }

        private static void assertType(@NotNull MqlType expected, @NotNull MqlType actual) {
            if (expected != actual) {
                throw new UnsupportedOperationException("Expected " + expected + ", got " + actual);
            }
        }
    }
}
