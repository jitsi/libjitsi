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
package org.jitsi.impl.neomedia.codec.audio.ilbc;

/**
 * @author Jean Lorchat
 */
class bitpack {

    int firstpart;
    int rest;

    public bitpack() {
    firstpart = 0;
    rest = 0;
    }

    public bitpack(int fp, int r) {
    firstpart = fp;
    rest = r;
    }

    public int get_firstpart() {
    return firstpart;
    }

    public void set_firstpart(int fp) {
    firstpart = fp;
    }

    public int get_rest() {
    return rest;
    }

    public void set_rest(int r) {
    rest = r;
    }

}
