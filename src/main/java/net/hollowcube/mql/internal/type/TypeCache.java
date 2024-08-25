package net.hollowcube.mql.internal.type;

import net.hollowcube.mql.internal.tree.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeCache {

    // We map expr trees to their types. Once a tree has been computed it is final.
    private final Map<MqlExpr, MqlType> cache = new HashMap<>();
    private final TypeCheckingVisitor visitor = new TypeCheckingVisitor();

    private final Map<String, Class<?>> contextObjects;

    public TypeCache(@NotNull Map<String, Class<?>> contextObjects) {
        this.contextObjects = contextObjects;
    }

    public @NotNull MqlType getType(@NotNull MqlExpr expr) {
        return cache.computeIfAbsent(expr, e -> e.visit(visitor, this));
    }

    // Note: There is a slightly odd back and forth here going on here with the cache
    // and the visitor. Whenever we request a type using getType it returns the cached
    // value or calls MqlExpr#visit. Note that it calls visit on the node directly
    // which sends it to visit___Expr NOT to `visit`.
    // In the visitor impls we use `visit` to call the next node in the tree, which
    // directs it to the cache again to avoid recomputing large trees.

    private class TypeCheckingVisitor implements MqlVisitor<TypeCache, MqlType> {
        private static final List<MqlType> STRING_OR_NUMBER = List.of(MqlType.STRING, MqlType.NUMBER);

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
            var lhsType = visit(expr.lhs(), typeCache);
            var rhsType = visit(expr.rhs(), typeCache);

            return switch (expr.operator()) {
                case PLUS, MINUS, DIV, MUL, GTE, GE, LTE, LE -> {
                    assertType(MqlType.NUMBER, lhsType);
                    assertType(MqlType.NUMBER, rhsType);
                    // For GTE, GE, LTE, LE this number is 1 or 0.
                    yield MqlType.NUMBER;
                }
                case EQ, NEQ -> {
                    // Both must be a string or number
                    assertType(STRING_OR_NUMBER, lhsType);
                    // Both must be the same type
                    assertType(lhsType, rhsType, String.format("Expected same types: %s != %s", lhsType, rhsType));
                    yield MqlType.NUMBER; // 1 or 0
                }
                case NULL_COALESCE -> {
                    assertType(lhsType, rhsType, String.format("Expected same types: %s != %s", lhsType, rhsType));
                    yield lhsType;
                }
            };
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
            // TODO: Probably not correct.
            return MqlType.NUMBER;
        }

        @Override
        public MqlType visitIndexExpr(@NotNull MqlIndexExpr expr, TypeCache typeCache) {
            throw new UnsupportedOperationException("not implemented");
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
            throw new UnsupportedOperationException("unreachable");
        }

        @Override
        public MqlType defaultValue() {
            throw new UnsupportedOperationException();
        }

        private static void assertType(@NotNull MqlType expected, @NotNull MqlType actual) {
            assertType(expected, actual, "Expected " + expected + ", got " + actual);
        }

        private static void assertType(@NotNull MqlType expected, @NotNull MqlType actual, @NotNull String message) {
            if (expected != actual) {
                throw new IllegalStateException(message);
            }
        }

        private static void assertType(@NotNull List<MqlType> expected, @NotNull MqlType actual) {
            if (!expected.contains(actual)) {
                throw new IllegalStateException("Expected " + expected + ", got " + actual);
            }
        }
    }
}
