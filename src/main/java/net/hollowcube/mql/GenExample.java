package net.hollowcube.mql;

import net.hollowcube.mql.internal.MqlRuntime;
import org.jetbrains.annotations.NotNull;

public class GenExample implements MqlModule.Instance {
    private static final int MODULE_INDEX = 0; // Incremented for each module compiled during one vm runtime.
    private final Object[] scripts = new Object[]{
            new ScriptA(), new ScriptB()
    };

    private ContentError.Handler contentErrorHandler;

    // Variables used by scripts
    private double x;
    private double y;
    private double z;

    GenExample(ContentError.Handler contentErrorHandler) {
        {   // Initializer 1
            double x = 0;
            double y = 0;
            //...

            System.out.println(x + y);
        }
        {   // Initializer 2
            //...
        }
    }

    @Override
    public <T> @NotNull T getScript(@NotNull MqlCompiler.Unit<T> ref) {
        return MqlRuntime.getScript(MODULE_INDEX, scripts, ref);
    }

    class ScriptA { //todo implement
        public void run() {
            x = 1;
            y = 2;
            double lhs = 2, rhs = 0;
            if (rhs == 0) {
                z = Double.NaN;
            } else {
                z = lhs / rhs;
            }
        }
    }

    class ScriptB {
        public void run() {
            x = 1;
            y = 2;
            z = 3;
        }
    }

}
