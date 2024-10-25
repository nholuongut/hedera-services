/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import com.swirlds.demo.platform.fs.stresstest.proto.AppTransactionSignatureType;

/**
 * Configuration for generating payload with each different type of signature
 */
public class SigTypeConfig {
    /** Signing algorithm used to sign a payload */
    public AppTransactionSignatureType signatureType;
    /** percentage of total traffic of this kind of signing payload */
    public int percentage;

    public AppTransactionSignatureType getSignatureType() {
        return signatureType;
    }

    public void setSignatureType(AppTransactionSignatureType signatureType) {
        this.signatureType = signatureType;
    }

    public int getPercentage() {
        return percentage;
    }

    public void setPercentage(int percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("percentage");
        }

        this.percentage = percentage;
    }
}
