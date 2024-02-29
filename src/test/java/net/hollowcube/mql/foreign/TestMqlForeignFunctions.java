package net.hollowcube.mql.foreign;

import net.hollowcube.mql.tree.MqlNumberExpr;
import net.hollowcube.mql.value.MqlCallable;
import net.hollowcube.mql.value.MqlValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMqlForeignFunctions {

    private static final AtomicBoolean test1Called = new AtomicBoolean(false);

    public static void test1() {
        test1Called.set(true);
    }

    @Test
    public void emptyVoidFunction() throws Exception {
        Method method = getClass().getMethod("test1");
        MqlCallable function = MqlForeignFunctions.createForeign(method, null);
        assertEquals(0, function.arity());
        assertEquals(MqlValue.NULL, function.call(List.of(), null));
        assertTrue(test1Called.get());
    }

    private static final AtomicReference<Double> test2Value = new AtomicReference<>(0.0);

    public static void test2(double value) {
        test2Value.set(value);
    }

    @Test
    public void singleArgVoidFunction() throws Exception {
        Method method = getClass().getMethod("test2", double.class);
        MqlCallable function = MqlForeignFunctions.createForeign(method, null);
        MqlValue result = function.call(List.of(new MqlNumberExpr(10.5)), null);

        assertEquals(1, function.arity());
        assertEquals(MqlValue.NULL, result);
        assertEquals(10.5, test2Value.get());
    }

    public static double test3() {
        return 10.5;
    }

    @Test
    public void emptyNonVoidFunction() throws Exception {
        Method method = getClass().getMethod("test3");
        MqlCallable function = MqlForeignFunctions.createForeign(method, null);
        MqlValue result = function.call(List.of(), null);

        assertEquals(0, function.arity());
        assertEquals(MqlValue.from(10.5), result);
    }

    public static double test4(double a, double b) {
        return a + b;
    }

    @Test
    public void multiParamNonVoidFunction() throws Exception {
        Method method = getClass().getMethod("test4", double.class, double.class);
        MqlCallable function = MqlForeignFunctions.createForeign(method, null);
        MqlValue result = function.call(List.of(new MqlNumberExpr(10.5), new MqlNumberExpr(20.5)), null);

        assertEquals(2, function.arity());
        assertEquals(MqlValue.from(31), result);
    }
}
