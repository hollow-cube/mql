package net.hollowcube.mql;

import net.hollowcube.mql.foreign.MqlEnv;
import net.hollowcube.mql.foreign.Query;
import net.hollowcube.mql.internal.ScriptInterfaceEmpty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMqlCompiler {

    static class MyQuery {

        @Query("x_velocity")
        public double getXVelocity() {
            // could come from wherever
            return 1;
        }

    }

    interface MyScript {
        double eval(@MqlEnv({"q", "query"}) MyQuery query);
    }

    @Test
    void testTheStuff() {

        var compiler = MqlCompiler.create();
        compiler.addInitializer("v.x = 22", null);
        var getXUnit = compiler.addScript(ScriptInterfaceEmpty.class, "v.x", true, null);
        var incXUnit = compiler.addScript(ScriptInterfaceEmpty.class, "v.x = v.x + 1", true, null);
        var module = compiler.compile();

        var instance = module.newInstance();
        var getX = instance.getScript(getXUnit);
        var incX = instance.getScript(incXUnit);

        assertEquals(22, getX.eval());
        incX.eval();
        assertEquals(23, getX.eval());


    }

    @Test
    void testAgain() {
        var compiler = MqlCompiler.create();
        var scriptRef = compiler.addScript(MyScript.class, "q.x_velocity + 1", true, null);
        var module = compiler.compile();

        var instance = module.newInstance();
        var script = instance.getScript(scriptRef);

        var query = new MyQuery();
        assertEquals(2, script.eval(query));

    }
}
