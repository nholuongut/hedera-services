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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.token.impl.handlers.transfer.NFTOwnersChangeStep.validateSpenderHasAllowance;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsableForAliasedId;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.config.data.EntitiesConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Associates the token with the sender and receiver accounts if they are not already associated.
 * They are auto-associated only if there are open auto-associations available on the account.
 */
public class AssociateTokenRecipientsStep extends BaseTokenHandler implements TransferStep {
    private final CryptoTransferTransactionBody op;

    /**
     * Constructs the step with the operation.
     * @param op the operation
     */
    public AssociateTokenRecipientsStep(@NonNull final CryptoTransferTransactionBody op) {
        this.op = requireNonNull(op);
    }

    @Override
    public void doIn(@NonNull final TransferContext transferContext) {
        requireNonNull(transferContext);
        final var handleContext = transferContext.getHandleContext();
        final var storeFactory = handleContext.storeFactory();
        final var tokenStore = storeFactory.writableStore(WritableTokenStore.class);
        final var tokenRelStore = storeFactory.writableStore(WritableTokenRelationStore.class);
        final var accountStore = storeFactory.writableStore(WritableAccountStore.class);
        final var nftStore = storeFactory.writableStore(WritableNftStore.class);
        final List<TokenAssociation> newAssociations = new ArrayList<>();

        for (final var xfers : op.tokenTransfers()) {
            final var tokenId = xfers.tokenOrThrow();
            final var token = getIfUsable(tokenId, tokenStore);

            for (final var aa : xfers.transfers()) {
                final var accountId = aa.accountIDOrElse(AccountID.DEFAULT);
                final TokenAssociation newAssociation;
                try {
                    newAssociation = validateAndBuildAutoAssociation(
                            accountId, tokenId, token, accountStore, tokenRelStore, handleContext);
                } catch (HandleException e) {
                    // (FUTURE) Remove this catch and stop translating TOKEN_NOT_ASSOCIATED_TO_ACCOUNT
                    // into e.g. SPENDER_DOES_NOT_HAVE_ALLOWANCE; we need this only for mono-service
                    // fidelity during diff testing
                    if (mayNeedTranslation(e, aa)) {
                        validateFungibleAllowance(
                                requireNonNull(accountStore.getAccountById(aa.accountIDOrThrow())),
                                handleContext.payer(),
                                tokenId,
                                aa.amount());
                    }
                    throw e;
                }
                if (newAssociation != null) {
                    newAssociations.add(newAssociation);
                }
            }

            for (final var nftTransfer : xfers.nftTransfers()) {
                final var receiverId = nftTransfer.receiverAccountIDOrElse(AccountID.DEFAULT);
                final var senderId = nftTransfer.senderAccountIDOrElse(AccountID.DEFAULT);
                // sender should be associated already. If not throw exception
                final var nft = nftStore.get(tokenId, nftTransfer.serialNumber());
                try {
                    validateTrue(tokenRelStore.get(senderId, tokenId) != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
                } catch (HandleException e) {
                    // (FUTURE) Remove this catch and stop translating TOKEN_NOT_ASSOCIATED_TO_ACCOUNT
                    // into e.g. SPENDER_DOES_NOT_HAVE_ALLOWANCE; we need this only for mono-service
                    // fidelity during diff testing
                    if (nft != null && mayNeedTranslation(e, nftTransfer)) {
                        validateSpenderHasAllowance(
                                requireNonNull(accountStore.getAccountById(senderId)),
                                handleContext.payer(),
                                tokenId,
                                nft);
                    }
                    throw e;
                }
                validateTrue(nft != null, INVALID_NFT_ID);
                final var newAssociation = validateAndBuildAutoAssociation(
                        receiverId, tokenId, token, accountStore, tokenRelStore, handleContext);
                if (newAssociation != null) {
                    newAssociations.add(newAssociation);
                }
            }
        }

        for (TokenAssociation newAssociation : newAssociations) {
            transferContext.addToAutomaticAssociations(newAssociation);
        }
    }

    private boolean mayNeedTranslation(final HandleException e, final AccountAmount adjustment) {
        return e.getStatus() == TOKEN_NOT_ASSOCIATED_TO_ACCOUNT && adjustment.isApproval() && adjustment.amount() < 0;
    }

    private boolean mayNeedTranslation(final HandleException e, final NftTransfer nftTransfer) {
        return e.getStatus() == TOKEN_NOT_ASSOCIATED_TO_ACCOUNT && nftTransfer.isApproval();
    }

    /**
     * Associates the token with the account if it is not already associated. It is auto-associated only if there are
     * open auto-associations available on the account.
     *
     * @param accountId The account to associate the token with
     * @param tokenId The tokenID of the token to associate with the account
     * @param token The token to associate with the account
     * @param accountStore The account store
     * @param tokenRelStore The token relation store
     * @param context The context
     */
    private TokenAssociation validateAndBuildAutoAssociation(
            @NonNull final AccountID accountId,
            @NonNull final TokenID tokenId,
            @NonNull final Token token,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final HandleContext context) {
        final var account =
                getIfUsableForAliasedId(accountId, accountStore, context.expiryValidator(), INVALID_ACCOUNT_ID);
        final var tokenRel = tokenRelStore.get(accountId, tokenId);
        final var config = context.configuration();
        final var entitiesConfig = config.getConfigData(EntitiesConfig.class);

        if (tokenRel == null && account.maxAutoAssociations() != 0) {
            boolean validAssociations = hasUnlimitedAutoAssociations(account, entitiesConfig)
                    || account.usedAutoAssociations() < account.maxAutoAssociations();
            validateTrue(validAssociations, NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
            validateFalse(token.hasKycKey(), ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN);
            validateFalse(token.accountsFrozenByDefault(), ACCOUNT_FROZEN_FOR_TOKEN);

            final var unlimitedAssociationsEnabled =
                    config.getConfigData(EntitiesConfig.class).unlimitedAutoAssociationsEnabled();
            if (unlimitedAssociationsEnabled) {
                dispatchAutoAssociation(token, accountStore, tokenRelStore, context, account);
                // We still need to return this association to the caller. Since this is used to set in record automatic
                // associations.
                return asTokenAssociation(tokenId, accountId);
            } else {
                // Once the unlimitedAssociationsEnabled is enabled, this block of code can be removed and
                // all auto-associations will be done through the synthetic transaction and charged.
                final var newRelation = autoAssociate(account, token, accountStore, tokenRelStore, config);
                return asTokenAssociation(newRelation.tokenId(), newRelation.accountId());
            }
        } else {
            validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
            validateFalse(tokenRel.frozen(), ACCOUNT_FROZEN_FOR_TOKEN);
            return null;
        }
    }

    /**
     * Dispatches a synthetic transaction to associate the token with the account. It will increment the usedAutoAssociations
     * count on the account and set the token relation as automaticAssociation.
     * This is done only if the unlimitedAutoAssociationsEnabled is enabled.
     * @param token The token to associate with the account
     * @param accountStore The account store
     * @param tokenRelStore The token relation store
     * @param context The context
     * @param account The account to associate the token with
     */
    private void dispatchAutoAssociation(
            final @NonNull Token token,
            final @NonNull WritableAccountStore accountStore,
            final @NonNull WritableTokenRelationStore tokenRelStore,
            final @NonNull HandleContext context,
            final @NonNull Account account) {
        final var accountId = account.accountIdOrThrow();
        final var tokenId = token.tokenIdOrThrow();
        final var syntheticAssociation = TransactionBody.newBuilder()
                .tokenAssociate(TokenAssociateTransactionBody.newBuilder()
                        .account(account.accountId())
                        .tokens(token.tokenId())
                        .build())
                .build();
        // We don't need to verify signatures for this internal dispatch. So we specify the keyVerifier to null
        context.dispatchRemovablePrecedingTransaction(
                syntheticAssociation, SingleTransactionRecordBuilder.class, null, context.payer());
        // increment the usedAutoAssociations count
        final var accountModified = requireNonNull(accountStore.getAliasedAccountById(accountId))
                .copyBuilder()
                .usedAutoAssociations(account.usedAutoAssociations() + 1)
                .build();
        accountStore.put(accountModified);
        // We need to set this as auto-association
        final var newTokenRel = requireNonNull(tokenRelStore.get(accountId, tokenId))
                .copyBuilder()
                .automaticAssociation(true)
                .build();
        tokenRelStore.put(newTokenRel);
    }

    private void validateFungibleAllowance(
            @NonNull final Account account,
            @NonNull final AccountID topLevelPayer,
            @NonNull final TokenID tokenId,
            final long amount) {
        final var tokenAllowances = account.tokenAllowances();
        for (final var allowance : tokenAllowances) {
            if (topLevelPayer.equals(allowance.spenderId()) && tokenId.equals(allowance.tokenId())) {
                final var newAllowanceAmount = allowance.amount() + amount;
                validateTrue(newAllowanceAmount >= 0, AMOUNT_EXCEEDS_ALLOWANCE);
                return;
            }
        }
        throw new HandleException(SPENDER_DOES_NOT_HAVE_ALLOWANCE);
    }
}
