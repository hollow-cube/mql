package net.hollowcube.mql.jit;

import net.hollowcube.mql.foreign.MqlEnv;

public interface QueryScript {
    double evaluate(@MqlEnv({"query", "q"}) QueryTest query);
}
