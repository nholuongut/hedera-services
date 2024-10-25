/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.dsl.entities;

import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static java.util.Collections.emptyList;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.SpecOperation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Represents a non-fungible token that may exist on one or more target networks and be
 * registered with more than one {@link com.hedera.services.bdd.spec.HapiSpec} if desired.
 */
public class SpecNonFungibleToken extends SpecToken {
    private int numPreMints = 0;

    public SpecNonFungibleToken(@NonNull String name) {
        super(name, NON_FUNGIBLE_UNIQUE);
    }

    /**
     * Returns a representation of the requested serial number for this token.
     *
     * @param serialNo the serial number
     * @return the representation
     */
    public SpecNft serialNo(final long serialNo) {
        return new SpecNft(this, serialNo);
    }

    /**
     * Sets the number of pre-mints to perform.
     *
     * @param numPreMints the number of pre-mints to perform
     */
    public void setNumPreMints(final int numPreMints) {
        if (numPreMints > 10) {
            throw new IllegalArgumentException("Cannot pre-mint more than 10 NFTs");
        }
        this.numPreMints = numPreMints;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<SpecOperation> postSuccessOps() {
        return numPreMints > 0 ? List.of(mintToken(name, snMetadata(numPreMints))) : emptyList();
    }

    private List<ByteString> snMetadata(final int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> ByteString.copyFromUtf8("SN#" + (i + 1)))
                .toList();
    }
}
