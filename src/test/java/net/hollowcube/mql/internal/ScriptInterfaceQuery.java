package net.hollowcube.mql.internal;

import net.hollowcube.mql.foreign.MqlEnv;
import net.hollowcube.mql.foreign.Query;

public interface ScriptInterfaceQuery {

    double eval(@MqlEnv({"query", "q"}) MyQuery query);

    final class MyQuery {

        @Query
        public double inc(double value) {
            return value + 1;
        }

    }

}
