/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.info;

import static com.hedera.hapi.util.HapiUtils.parseAccount;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.system.address.Address;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.cert.CertificateEncodingException;

public record SelfNodeInfoImpl(
        long nodeId,
        @NonNull AccountID accountId,
        long stake,
        @NonNull String externalHostName,
        int externalPort,
        @NonNull String internalHostName,
        int internalPort,
        @NonNull String hexEncodedPublicKey,
        @NonNull String memo,
        @Nullable Bytes sigCertBytes,
        @NonNull HederaSoftwareVersion version,
        @NonNull String selfName)
        implements SelfNodeInfo {

    public SelfNodeInfoImpl {
        requireNonNull(accountId);
        requireNonNull(memo);
        requireNonNull(version);
        if (nodeId < 0) {
            throw new IllegalArgumentException("node ID cannot be less than 0");
        }
    }

    @NonNull
    public static SelfNodeInfo of(@NonNull final Address address, @NonNull final HederaSoftwareVersion version) {
        final var sigCert = address.getSigCert();
        Bytes sigCertBytes;
        try {
            sigCertBytes = sigCert == null ? Bytes.EMPTY : Bytes.wrap(sigCert.getEncoded());
        } catch (CertificateEncodingException e) {
            sigCertBytes = Bytes.EMPTY;
        }
        return new SelfNodeInfoImpl(
                address.getNodeId().id(),
                parseAccount(address.getMemo()),
                address.getWeight(),
                requireNonNull(address.getHostnameExternal()),
                address.getPortExternal(),
                requireNonNull(address.getHostnameInternal()),
                address.getPortInternal(),
                CommonUtils.hex(requireNonNull(address.getSigPublicKey()).getEncoded()),
                address.getMemo(),
                sigCertBytes,
                version,
                address.getSelfName());
    }

    @NonNull
    @Override
    public SemanticVersion hapiVersion() {
        return version.getHapiVersion();
    }

    @NonNull
    @Override
    public SemanticVersion appVersion() {
        return version.getPbjSemanticVersion();
    }
}
