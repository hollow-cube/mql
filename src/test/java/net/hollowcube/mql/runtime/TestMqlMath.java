package net.hollowcube.mql.runtime;

import net.hollowcube.mql.tree.MqlNumberExpr;
import net.hollowcube.mql.value.MqlCallable;
import net.hollowcube.mql.value.MqlValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMqlMath {

    @Test
    public void testForeignMath() {
        MqlValue result = MqlMath.INSTANCE.get("sqrt").cast(MqlCallable.class)
                .call(List.of(new MqlNumberExpr(4)), null);

        assertEquals(MqlValue.from(2), result);
    }

    // hermiteBlend
    // lerp
    // lerprotate

}
