package org.jitsi.util.function;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * Created by bbaldino on 8/1/17.
 */
public class SeqNumTranslationTest
{
    @Test
    public void apply()
        throws
        Exception
    {
        SeqNumTranslation newSNT = new SeqNumTranslation(-9);
        SeqNumTranslation oldSNT = new SeqNumTranslation(65527);

        for (int i = 0; i < 65535; ++i)
        {
            assert(newSNT.apply(10).equals(oldSNT.apply(10)));
        }
    }

}