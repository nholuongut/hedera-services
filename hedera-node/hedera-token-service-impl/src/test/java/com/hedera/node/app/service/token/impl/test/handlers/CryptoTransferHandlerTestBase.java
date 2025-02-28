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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.service.token.impl.test.handlers.transfer.StepsBase;
import com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoTransferHandlerTestBase extends StepsBase {
    protected static final AccountID ACCOUNT_3333 = asAccount(3333);
    protected static final AccountID ACCOUNT_4444 = asAccount(4444);
    protected static final TokenID TOKEN_2468 = asToken(2468);

    protected static final AccountAmount ACCT_3333_MINUS_10 =
            AccountAmount.newBuilder().accountID(ACCOUNT_3333).amount(-10).build();
    protected static final AccountAmount ACCT_4444_MINUS_5 =
            AccountAmount.newBuilder().accountID(ACCOUNT_4444).amount(-5).build();
    protected static final AccountAmount ACCT_3333_PLUS_5 =
            AccountAmount.newBuilder().accountID(ACCOUNT_3333).amount(5).build();
    protected static final AccountAmount ACCT_4444_PLUS_10 =
            AccountAmount.newBuilder().accountID(ACCOUNT_4444).amount(10).build();
    protected static final NftTransfer SERIAL_1_FROM_3333_TO_4444 = NftTransfer.newBuilder()
            .serialNumber(1)
            .senderAccountID(ACCOUNT_3333)
            .receiverAccountID(ACCOUNT_4444)
            .build();
    protected static final NftTransfer SERIAL_2_FROM_4444_TO_3333 = NftTransfer.newBuilder()
            .serialNumber(2)
            .senderAccountID(ACCOUNT_4444)
            .receiverAccountID(ACCOUNT_3333)
            .build();

    protected CryptoTransferHandler subject;
    protected CryptoTransferValidator validator;

    @BeforeEach
    public void setUp() {
        super.setUp();
        validator = new CryptoTransferValidator();
        subject = new CryptoTransferHandler(validator);
    }

    protected TransactionBody newCryptoTransfer(final AccountAmount... acctAmounts) {
        return newCryptoTransfer(Arrays.stream(acctAmounts).toList(), List.of());
    }

    protected TransactionBody newCryptoTransfer(final TokenTransferList... tokenTransferLists) {
        return newCryptoTransfer(List.of(), Arrays.stream(tokenTransferLists).toList());
    }

    // Note: `tokenTransferLists` can include both fungible and non-fungible token transfers
    protected TransactionBody newCryptoTransfer(
            final List<AccountAmount> acctAmounts, final List<TokenTransferList> tokenTransferLists) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(ACCOUNT_3333))
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .transfers(TransferList.newBuilder().accountAmounts(acctAmounts))
                        .tokenTransfers(tokenTransferLists))
                .build();
    }
}
