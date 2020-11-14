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
package org.jitsi.impl.neomedia.transform.dtls;

import java.security.*;
import org.bouncycastle.tls.crypto.impl.bc.*;

/**
 * Utilities and constants for use with the BouncyCastle API.
 */
public class DtlsUtils
{
    /**
     * A crypto instance initialized with the default {@link SecureRandom}.
     */
    public static final BcTlsCrypto BC_TLS_CRYPTO =
        new BcTlsCrypto(new SecureRandom());
}
