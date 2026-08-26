package com.data.job;

import com.data.expression.ParameterUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ParaTest {

    @Test
    @Timeout(6000000)
    public void testParameterUtil() throws Exception {
        String text = "#{NOW - 1d, yyyy-MM-dd HH:mm:ss}";
        String result = ParameterUtil.replaceParameters( text);
        System.out.println(result);
        System.out.println(result);
    }

    @Test
    @Timeout(6000000)
    public void testParameterUtilNoFormat() throws Exception {
        String text = "#{NOW - 2d}";
        String result = ParameterUtil.replaceParameters( text);
        System.out.println(result);
    }

    @Test
    @Timeout(6000000)
    public void testParameterUtilRandom() throws Exception {
        String text = "#{RANDOM}";
        String result = ParameterUtil.replaceParameters( text);
        System.out.println(result);
    }
}
