/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.util.function;

/**
 * @author George Politis
 */
public class SeqNumTranslation
    extends AbstractFunction<Integer, Integer>
{
    /**
     * The delta to apply to the sequence number that is specified as an
     * argument in the apply method.
     */
    private final int seqNumDelta;

    /**
     * Ctor.
     *
     * @param seqNumDelta The delta to apply to the sequence number that is
     * specified as an argument in the apply method.
     */
    public SeqNumTranslation(int seqNumDelta)
    {
        this.seqNumDelta = seqNumDelta;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer apply(Integer seqNum)
    {
        return seqNumDelta == 0
            ? seqNum : (seqNum + seqNumDelta) & 0xFFFF;
    }
}
