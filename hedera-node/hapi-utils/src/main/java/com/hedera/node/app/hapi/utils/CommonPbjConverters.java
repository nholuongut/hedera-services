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

package com.hedera.node.app.hapi.utils;

import static com.hedera.node.app.hapi.utils.ByteStringUtils.unwrapUnsafelyIfPossible;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FeeComponents;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleInfo;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.hederahashgraph.api.proto.java.AccountID.AccountCase;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class CommonPbjConverters {
    public static @NonNull com.hederahashgraph.api.proto.java.Query fromPbj(@NonNull Query query) {
        requireNonNull(query);
        try {
            final var bytes = asBytes(Query.PROTOBUF, query);
            return com.hederahashgraph.api.proto.java.Query.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public static com.hederahashgraph.api.proto.java.File fromPbj(@Nullable File file) {
        var builder = com.hederahashgraph.api.proto.java.File.newBuilder();
        if (file != null) {
            builder.setFileId(fromPbj(file.fileIdOrThrow()));
            builder.setExpirationSecond(file.expirationSecond());
            builder.setKeys(pbjToProto(
                    file.keysOrElse(KeyList.DEFAULT), KeyList.class, com.hederahashgraph.api.proto.java.KeyList.class));
            builder.setContents(ByteString.copyFrom(file.contents().toByteArray()));
            builder.setMemo(file.memo());
            builder.setDeleted(file.deleted());
        }
        return builder.build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.EntityNumber fromPbj(@NonNull EntityNumber entityNumber) {
        requireNonNull(entityNumber);
        return com.hederahashgraph.api.proto.java.EntityNumber.newBuilder()
                .setNumber(entityNumber.number())
                .build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.TransactionBody fromPbj(@NonNull TransactionBody tx) {
        requireNonNull(tx);
        try {
            final var bytes = asBytes(TransactionBody.PROTOBUF, tx);
            return com.hederahashgraph.api.proto.java.TransactionBody.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.Key fromPbj(@NonNull Key keyValue) {
        requireNonNull(keyValue);
        try {
            final var bytes = asBytes(Key.PROTOBUF, keyValue);
            return com.hederahashgraph.api.proto.java.Key.parseFrom(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T extends Record> byte[] asBytes(@NonNull Codec<T> codec, @NonNull T tx) {
        requireNonNull(codec);
        requireNonNull(tx);
        try {
            final var bytes = new ByteArrayOutputStream();
            codec.write(tx, new WritableStreamingData(bytes));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Unable to convert from PBJ to bytes", e);
        }
    }

    public static @NonNull byte[] asBytes(@NonNull Bytes b) {
        final var buf = new byte[Math.toIntExact(b.length())];
        b.getBytes(0, buf);
        return buf;
    }

    /**
     * Tries to convert a PBJ {@link ResponseType} to a {@link com.hederahashgraph.api.proto.java.ResponseType}
     *
     * @param responseType the PBJ {@link ResponseType} to convert
     * @return the converted {@link com.hederahashgraph.api.proto.java.ResponseType} if valid
     */
    public static @NonNull com.hederahashgraph.api.proto.java.ResponseType fromPbjResponseType(
            @NonNull final ResponseType responseType) {
        return switch (requireNonNull(responseType)) {
            case ANSWER_ONLY -> com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
            case ANSWER_STATE_PROOF -> com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
            case COST_ANSWER -> com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
            case COST_ANSWER_STATE_PROOF -> com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER_STATE_PROOF;
        };
    }

    public static <T extends Record, R extends GeneratedMessageV3> R pbjToProto(
            final T pbj, final Class<T> pbjClass, final Class<R> protoClass) {
        try {
            final var codecField = pbjClass.getDeclaredField("PROTOBUF");
            final var codec = (Codec<T>) codecField.get(null);
            final var bytes = asBytes(codec, pbj);
            final var protocParser = protoClass.getMethod("parseFrom", byte[].class);
            return (R) protocParser.invoke(null, bytes);
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            // Should be impossible, so just propagate an exception
            throw new RuntimeException("Invalid conversion to proto for " + pbjClass.getSimpleName(), e);
        }
    }

    public static com.hederahashgraph.api.proto.java.FileID fromPbj(final FileID someFileId) {
        return com.hederahashgraph.api.proto.java.FileID.newBuilder()
                .setRealmNum(someFileId.realmNum())
                .setShardNum(someFileId.shardNum())
                .setFileNum(someFileId.fileNum())
                .build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.TransactionRecord fromPbj(@NonNull TransactionRecord tx) {
        requireNonNull(tx);
        try {
            final var bytes = asBytes(TransactionRecord.PROTOBUF, tx);
            return com.hederahashgraph.api.proto.java.TransactionRecord.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static com.hederahashgraph.api.proto.java.CustomFee fromPbj(@NonNull final CustomFee customFee) {
        return explicitPbjToProto(
                customFee, CustomFee.PROTOBUF, com.hederahashgraph.api.proto.java.CustomFee::parseFrom);
    }

    private interface ProtoParser<R extends GeneratedMessageV3> {
        R parseFrom(byte[] bytes) throws InvalidProtocolBufferException;
    }

    private static <T extends Record, R extends GeneratedMessageV3> R explicitPbjToProto(
            @NonNull final T pbj, @NonNull final Codec<T> pbjCodec, @NonNull final ProtoParser<R> protoParser) {
        requireNonNull(pbj);
        requireNonNull(pbjCodec);
        requireNonNull(protoParser);
        try {
            return protoParser.parseFrom(asBytes(pbjCodec, pbj));
        } catch (InvalidProtocolBufferException e) {
            // Should be impossible
            throw new IllegalStateException("Serialization failure for " + pbj, e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.SubType fromPbj(@NonNull SubType subType) {
        requireNonNull(subType);
        return com.hederahashgraph.api.proto.java.SubType.valueOf(subType.name());
    }

    public static @NonNull TokenID toPbj(@NonNull com.hederahashgraph.api.proto.java.TokenID tokenID) {
        requireNonNull(tokenID);
        return TokenID.newBuilder()
                .shardNum(tokenID.getShardNum())
                .realmNum(tokenID.getRealmNum())
                .tokenNum(tokenID.getTokenNum())
                .build();
    }

    public static @NonNull AccountID toPbj(@NonNull com.hederahashgraph.api.proto.java.AccountID accountID) {
        requireNonNull(accountID);
        final var builder =
                AccountID.newBuilder().shardNum(accountID.getShardNum()).realmNum(accountID.getRealmNum());
        if (accountID.getAccountCase() == AccountCase.ALIAS) {
            builder.alias(Bytes.wrap(accountID.getAlias().toByteArray()));
        } else {
            builder.accountNum(accountID.getAccountNum());
        }
        return builder.build();
    }

    public static @NonNull EntityNumber toPbj(@NonNull com.hederahashgraph.api.proto.java.EntityNumber entityNumber) {
        requireNonNull(entityNumber);
        final var builder = EntityNumber.newBuilder().number(entityNumber.getNumber());
        return builder.build();
    }

    public static <T extends GeneratedMessageV3, R extends Record> @NonNull R protoToPbj(
            @NonNull final T proto, @NonNull final Class<R> pbjClass) {
        try {
            final var bytes = requireNonNull(proto).toByteArray();
            final var codecField = requireNonNull(pbjClass).getDeclaredField("PROTOBUF");
            final var codec = (Codec<R>) codecField.get(null);
            return codec.parse(BufferedData.wrap(bytes));
        } catch (NoSuchFieldException | IllegalAccessException | ParseException e) {
            // Should be impossible, so just propagate an exception
            throw new RuntimeException("Invalid conversion to PBJ for " + pbjClass.getSimpleName(), e);
        }
    }

    public static @NonNull HederaFunctionality toPbj(
            @NonNull com.hederahashgraph.api.proto.java.HederaFunctionality function) {
        requireNonNull(function);
        return switch (function) {
            case Freeze -> HederaFunctionality.FREEZE;
            case GetByKey -> HederaFunctionality.GET_BY_KEY;
            case ConsensusCreateTopic -> HederaFunctionality.CONSENSUS_CREATE_TOPIC;
            case ConsensusDeleteTopic -> HederaFunctionality.CONSENSUS_DELETE_TOPIC;
            case ConsensusGetTopicInfo -> HederaFunctionality.CONSENSUS_GET_TOPIC_INFO;
            case ConsensusSubmitMessage -> HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE;
            case ConsensusUpdateTopic -> HederaFunctionality.CONSENSUS_UPDATE_TOPIC;
            case ContractAutoRenew -> HederaFunctionality.CONTRACT_AUTO_RENEW;
            case ContractCall -> HederaFunctionality.CONTRACT_CALL;
            case ContractCallLocal -> HederaFunctionality.CONTRACT_CALL_LOCAL;
            case ContractCreate -> HederaFunctionality.CONTRACT_CREATE;
            case ContractDelete -> HederaFunctionality.CONTRACT_DELETE;
            case ContractGetBytecode -> HederaFunctionality.CONTRACT_GET_BYTECODE;
            case ContractGetInfo -> HederaFunctionality.CONTRACT_GET_INFO;
            case ContractGetRecords -> HederaFunctionality.CONTRACT_GET_RECORDS;
            case ContractUpdate -> HederaFunctionality.CONTRACT_UPDATE;
            case CreateTransactionRecord -> HederaFunctionality.CREATE_TRANSACTION_RECORD;
            case CryptoAccountAutoRenew -> HederaFunctionality.CRYPTO_ACCOUNT_AUTO_RENEW;
            case CryptoAddLiveHash -> HederaFunctionality.CRYPTO_ADD_LIVE_HASH;
            case CryptoApproveAllowance -> HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE;
            case CryptoCreate -> HederaFunctionality.CRYPTO_CREATE;
            case CryptoDelete -> HederaFunctionality.CRYPTO_DELETE;
            case CryptoDeleteAllowance -> HederaFunctionality.CRYPTO_DELETE_ALLOWANCE;
            case CryptoDeleteLiveHash -> HederaFunctionality.CRYPTO_DELETE_LIVE_HASH;
            case CryptoGetAccountBalance -> HederaFunctionality.CRYPTO_GET_ACCOUNT_BALANCE;
            case CryptoGetAccountRecords -> HederaFunctionality.CRYPTO_GET_ACCOUNT_RECORDS;
            case CryptoGetInfo -> HederaFunctionality.CRYPTO_GET_INFO;
            case CryptoGetLiveHash -> HederaFunctionality.CRYPTO_GET_LIVE_HASH;
            case CryptoGetStakers -> HederaFunctionality.CRYPTO_GET_STAKERS;
            case CryptoTransfer -> HederaFunctionality.CRYPTO_TRANSFER;
            case CryptoUpdate -> HederaFunctionality.CRYPTO_UPDATE;
            case EthereumTransaction -> HederaFunctionality.ETHEREUM_TRANSACTION;
            case FileAppend -> HederaFunctionality.FILE_APPEND;
            case FileCreate -> HederaFunctionality.FILE_CREATE;
            case FileDelete -> HederaFunctionality.FILE_DELETE;
            case FileGetContents -> HederaFunctionality.FILE_GET_CONTENTS;
            case FileGetInfo -> HederaFunctionality.FILE_GET_INFO;
            case FileUpdate -> HederaFunctionality.FILE_UPDATE;
            case GetAccountDetails -> HederaFunctionality.GET_ACCOUNT_DETAILS;
            case GetBySolidityID -> HederaFunctionality.GET_BY_SOLIDITY_ID;
            case GetVersionInfo -> HederaFunctionality.GET_VERSION_INFO;
            case NetworkGetExecutionTime -> HederaFunctionality.NETWORK_GET_EXECUTION_TIME;
            case NONE -> HederaFunctionality.NONE;
            case NodeStakeUpdate -> HederaFunctionality.NODE_STAKE_UPDATE;
            case NodeCreate -> HederaFunctionality.NODE_CREATE;
            case NodeUpdate -> HederaFunctionality.NODE_UPDATE;
            case NodeDelete -> HederaFunctionality.NODE_DELETE;
            case ScheduleCreate -> HederaFunctionality.SCHEDULE_CREATE;
            case ScheduleDelete -> HederaFunctionality.SCHEDULE_DELETE;
            case ScheduleGetInfo -> HederaFunctionality.SCHEDULE_GET_INFO;
            case ScheduleSign -> HederaFunctionality.SCHEDULE_SIGN;
            case SystemDelete -> HederaFunctionality.SYSTEM_DELETE;
            case SystemUndelete -> HederaFunctionality.SYSTEM_UNDELETE;
            case TokenAccountWipe -> HederaFunctionality.TOKEN_ACCOUNT_WIPE;
            case TokenAssociateToAccount -> HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT;
            case TokenBurn -> HederaFunctionality.TOKEN_BURN;
            case TokenCreate -> HederaFunctionality.TOKEN_CREATE;
            case TokenDelete -> HederaFunctionality.TOKEN_DELETE;
            case TokenDissociateFromAccount -> HederaFunctionality.TOKEN_DISSOCIATE_FROM_ACCOUNT;
            case TokenFeeScheduleUpdate -> HederaFunctionality.TOKEN_FEE_SCHEDULE_UPDATE;
            case TokenFreezeAccount -> HederaFunctionality.TOKEN_FREEZE_ACCOUNT;
            case TokenGetAccountNftInfos -> HederaFunctionality.TOKEN_GET_ACCOUNT_NFT_INFOS;
            case TokenGetInfo -> HederaFunctionality.TOKEN_GET_INFO;
            case TokenGetNftInfo -> HederaFunctionality.TOKEN_GET_NFT_INFO;
            case TokenGetNftInfos -> HederaFunctionality.TOKEN_GET_NFT_INFOS;
            case TokenGrantKycToAccount -> HederaFunctionality.TOKEN_GRANT_KYC_TO_ACCOUNT;
            case TokenMint -> HederaFunctionality.TOKEN_MINT;
            case TokenPause -> HederaFunctionality.TOKEN_PAUSE;
            case TokenRevokeKycFromAccount -> HederaFunctionality.TOKEN_REVOKE_KYC_FROM_ACCOUNT;
            case TokenUnfreezeAccount -> HederaFunctionality.TOKEN_UNFREEZE_ACCOUNT;
            case TokenUnpause -> HederaFunctionality.TOKEN_UNPAUSE;
            case TokenUpdate -> HederaFunctionality.TOKEN_UPDATE;
            case TokenUpdateNfts -> HederaFunctionality.TOKEN_UPDATE_NFTS;
            case TokenReject -> HederaFunctionality.TOKEN_REJECT;
            case TransactionGetReceipt -> HederaFunctionality.TRANSACTION_GET_RECEIPT;
            case TransactionGetRecord -> HederaFunctionality.TRANSACTION_GET_RECORD;
            case TransactionGetFastRecord -> HederaFunctionality.TRANSACTION_GET_FAST_RECORD;
            case UncheckedSubmit -> HederaFunctionality.UNCHECKED_SUBMIT;
            case UtilPrng -> HederaFunctionality.UTIL_PRNG;
            case UNRECOGNIZED -> throw new RuntimeException("Unknown function UNRECOGNIZED");
        };
    }

    public static @NonNull SubType toPbj(@NonNull com.hederahashgraph.api.proto.java.SubType subType) {
        requireNonNull(subType);
        return switch (subType) {
            case DEFAULT -> SubType.DEFAULT;
            case TOKEN_FUNGIBLE_COMMON -> SubType.TOKEN_FUNGIBLE_COMMON;
            case TOKEN_NON_FUNGIBLE_UNIQUE -> SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
            case TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES -> SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
            case TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES -> SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
            case SCHEDULE_CREATE_CONTRACT_CALL -> SubType.SCHEDULE_CREATE_CONTRACT_CALL;
            case UNRECOGNIZED -> throw new IllegalArgumentException("Unknown subType UNRECOGNIZED");
        };
    }

    public static @NonNull ResponseCodeEnum toPbj(@NonNull com.hederahashgraph.api.proto.java.ResponseCodeEnum code) {
        return switch (requireNonNull(code)) {
            case OK -> ResponseCodeEnum.OK;
            case INVALID_TRANSACTION -> ResponseCodeEnum.INVALID_TRANSACTION;
            case PAYER_ACCOUNT_NOT_FOUND -> ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
            case INVALID_NODE_ACCOUNT -> ResponseCodeEnum.INVALID_NODE_ACCOUNT;
            case TRANSACTION_EXPIRED -> ResponseCodeEnum.TRANSACTION_EXPIRED;
            case INVALID_TRANSACTION_START -> ResponseCodeEnum.INVALID_TRANSACTION_START;
            case INVALID_TRANSACTION_DURATION -> ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
            case INVALID_SIGNATURE -> ResponseCodeEnum.INVALID_SIGNATURE;
            case MEMO_TOO_LONG -> ResponseCodeEnum.MEMO_TOO_LONG;
            case INSUFFICIENT_TX_FEE -> ResponseCodeEnum.INSUFFICIENT_TX_FEE;
            case INSUFFICIENT_PAYER_BALANCE -> ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
            case DUPLICATE_TRANSACTION -> ResponseCodeEnum.DUPLICATE_TRANSACTION;
            case BUSY -> ResponseCodeEnum.BUSY;
            case NOT_SUPPORTED -> ResponseCodeEnum.NOT_SUPPORTED;
            case INVALID_FILE_ID -> ResponseCodeEnum.INVALID_FILE_ID;
            case INVALID_ACCOUNT_ID -> ResponseCodeEnum.INVALID_ACCOUNT_ID;
            case INVALID_CONTRACT_ID -> ResponseCodeEnum.INVALID_CONTRACT_ID;
            case INVALID_TRANSACTION_ID -> ResponseCodeEnum.INVALID_TRANSACTION_ID;
            case RECEIPT_NOT_FOUND -> ResponseCodeEnum.RECEIPT_NOT_FOUND;
            case RECORD_NOT_FOUND -> ResponseCodeEnum.RECORD_NOT_FOUND;
            case INVALID_SOLIDITY_ID -> ResponseCodeEnum.INVALID_SOLIDITY_ID;
            case UNKNOWN -> ResponseCodeEnum.UNKNOWN;
            case SUCCESS -> ResponseCodeEnum.SUCCESS;
            case FAIL_INVALID -> ResponseCodeEnum.FAIL_INVALID;
            case FAIL_FEE -> ResponseCodeEnum.FAIL_FEE;
            case FAIL_BALANCE -> ResponseCodeEnum.FAIL_BALANCE;
            case KEY_REQUIRED -> ResponseCodeEnum.KEY_REQUIRED;
            case BAD_ENCODING -> ResponseCodeEnum.BAD_ENCODING;
            case INSUFFICIENT_ACCOUNT_BALANCE -> ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
            case INVALID_SOLIDITY_ADDRESS -> ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
            case INSUFFICIENT_GAS -> ResponseCodeEnum.INSUFFICIENT_GAS;
            case CONTRACT_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.CONTRACT_SIZE_LIMIT_EXCEEDED;
            case LOCAL_CALL_MODIFICATION_EXCEPTION -> ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
            case CONTRACT_REVERT_EXECUTED -> ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
            case CONTRACT_EXECUTION_EXCEPTION -> ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
            case INVALID_RECEIVING_NODE_ACCOUNT -> ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
            case MISSING_QUERY_HEADER -> ResponseCodeEnum.MISSING_QUERY_HEADER;
            case ACCOUNT_UPDATE_FAILED -> ResponseCodeEnum.ACCOUNT_UPDATE_FAILED;
            case INVALID_KEY_ENCODING -> ResponseCodeEnum.INVALID_KEY_ENCODING;
            case NULL_SOLIDITY_ADDRESS -> ResponseCodeEnum.NULL_SOLIDITY_ADDRESS;
            case CONTRACT_UPDATE_FAILED -> ResponseCodeEnum.CONTRACT_UPDATE_FAILED;
            case INVALID_QUERY_HEADER -> ResponseCodeEnum.INVALID_QUERY_HEADER;
            case INVALID_FEE_SUBMITTED -> ResponseCodeEnum.INVALID_FEE_SUBMITTED;
            case INVALID_PAYER_SIGNATURE -> ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
            case KEY_NOT_PROVIDED -> ResponseCodeEnum.KEY_NOT_PROVIDED;
            case INVALID_EXPIRATION_TIME -> ResponseCodeEnum.INVALID_EXPIRATION_TIME;
            case NO_WACL_KEY -> ResponseCodeEnum.NO_WACL_KEY;
            case FILE_CONTENT_EMPTY -> ResponseCodeEnum.FILE_CONTENT_EMPTY;
            case INVALID_ACCOUNT_AMOUNTS -> ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
            case EMPTY_TRANSACTION_BODY -> ResponseCodeEnum.EMPTY_TRANSACTION_BODY;
            case INVALID_TRANSACTION_BODY -> ResponseCodeEnum.INVALID_TRANSACTION_BODY;
            case INVALID_SIGNATURE_TYPE_MISMATCHING_KEY -> ResponseCodeEnum.INVALID_SIGNATURE_TYPE_MISMATCHING_KEY;
            case INVALID_SIGNATURE_COUNT_MISMATCHING_KEY -> ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY;
            case EMPTY_LIVE_HASH_BODY -> ResponseCodeEnum.EMPTY_LIVE_HASH_BODY;
            case EMPTY_LIVE_HASH -> ResponseCodeEnum.EMPTY_LIVE_HASH;
            case EMPTY_LIVE_HASH_KEYS -> ResponseCodeEnum.EMPTY_LIVE_HASH_KEYS;
            case INVALID_LIVE_HASH_SIZE -> ResponseCodeEnum.INVALID_LIVE_HASH_SIZE;
            case EMPTY_QUERY_BODY -> ResponseCodeEnum.EMPTY_QUERY_BODY;
            case EMPTY_LIVE_HASH_QUERY -> ResponseCodeEnum.EMPTY_LIVE_HASH_QUERY;
            case LIVE_HASH_NOT_FOUND -> ResponseCodeEnum.LIVE_HASH_NOT_FOUND;
            case ACCOUNT_ID_DOES_NOT_EXIST -> ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
            case LIVE_HASH_ALREADY_EXISTS -> ResponseCodeEnum.LIVE_HASH_ALREADY_EXISTS;
            case INVALID_FILE_WACL -> ResponseCodeEnum.INVALID_FILE_WACL;
            case SERIALIZATION_FAILED -> ResponseCodeEnum.SERIALIZATION_FAILED;
            case TRANSACTION_OVERSIZE -> ResponseCodeEnum.TRANSACTION_OVERSIZE;
            case TRANSACTION_TOO_MANY_LAYERS -> ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;
            case CONTRACT_DELETED -> ResponseCodeEnum.CONTRACT_DELETED;
            case PLATFORM_NOT_ACTIVE -> ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
            case KEY_PREFIX_MISMATCH -> ResponseCodeEnum.KEY_PREFIX_MISMATCH;
            case PLATFORM_TRANSACTION_NOT_CREATED -> ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
            case INVALID_RENEWAL_PERIOD -> ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
            case INVALID_PAYER_ACCOUNT_ID -> ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
            case ACCOUNT_DELETED -> ResponseCodeEnum.ACCOUNT_DELETED;
            case FILE_DELETED -> ResponseCodeEnum.FILE_DELETED;
            case ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS -> ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
            case SETTING_NEGATIVE_ACCOUNT_BALANCE -> ResponseCodeEnum.SETTING_NEGATIVE_ACCOUNT_BALANCE;
            case OBTAINER_REQUIRED -> ResponseCodeEnum.OBTAINER_REQUIRED;
            case OBTAINER_SAME_CONTRACT_ID -> ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
            case OBTAINER_DOES_NOT_EXIST -> ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
            case MODIFYING_IMMUTABLE_CONTRACT -> ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
            case FILE_SYSTEM_EXCEPTION -> ResponseCodeEnum.FILE_SYSTEM_EXCEPTION;
            case AUTORENEW_DURATION_NOT_IN_RANGE -> ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
            case ERROR_DECODING_BYTESTRING -> ResponseCodeEnum.ERROR_DECODING_BYTESTRING;
            case CONTRACT_FILE_EMPTY -> ResponseCodeEnum.CONTRACT_FILE_EMPTY;
            case CONTRACT_BYTECODE_EMPTY -> ResponseCodeEnum.CONTRACT_BYTECODE_EMPTY;
            case INVALID_INITIAL_BALANCE -> ResponseCodeEnum.INVALID_INITIAL_BALANCE;
            case INVALID_RECEIVE_RECORD_THRESHOLD -> ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
            case INVALID_SEND_RECORD_THRESHOLD -> ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
            case ACCOUNT_IS_NOT_GENESIS_ACCOUNT -> ResponseCodeEnum.ACCOUNT_IS_NOT_GENESIS_ACCOUNT;
            case PAYER_ACCOUNT_UNAUTHORIZED -> ResponseCodeEnum.PAYER_ACCOUNT_UNAUTHORIZED;
            case INVALID_FREEZE_TRANSACTION_BODY -> ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
            case FREEZE_TRANSACTION_BODY_NOT_FOUND -> ResponseCodeEnum.FREEZE_TRANSACTION_BODY_NOT_FOUND;
            case TRANSFER_LIST_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
            case RESULT_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
            case NOT_SPECIAL_ACCOUNT -> ResponseCodeEnum.NOT_SPECIAL_ACCOUNT;
            case CONTRACT_NEGATIVE_GAS -> ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
            case CONTRACT_NEGATIVE_VALUE -> ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
            case INVALID_FEE_FILE -> ResponseCodeEnum.INVALID_FEE_FILE;
            case INVALID_EXCHANGE_RATE_FILE -> ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE;
            case INSUFFICIENT_LOCAL_CALL_GAS -> ResponseCodeEnum.INSUFFICIENT_LOCAL_CALL_GAS;
            case ENTITY_NOT_ALLOWED_TO_DELETE -> ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
            case AUTHORIZATION_FAILED -> ResponseCodeEnum.AUTHORIZATION_FAILED;
            case FILE_UPLOADED_PROTO_INVALID -> ResponseCodeEnum.FILE_UPLOADED_PROTO_INVALID;
            case FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK -> ResponseCodeEnum.FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK;
            case FEE_SCHEDULE_FILE_PART_UPLOADED -> ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
            case EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED -> ResponseCodeEnum.EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED;
            case MAX_CONTRACT_STORAGE_EXCEEDED -> ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
            case TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT -> ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
            case TOTAL_LEDGER_BALANCE_INVALID -> ResponseCodeEnum.TOTAL_LEDGER_BALANCE_INVALID;
            case EXPIRATION_REDUCTION_NOT_ALLOWED -> ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
            case MAX_GAS_LIMIT_EXCEEDED -> ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
            case MAX_FILE_SIZE_EXCEEDED -> ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
            case RECEIVER_SIG_REQUIRED -> ResponseCodeEnum.RECEIVER_SIG_REQUIRED;
            case INVALID_TOPIC_ID -> ResponseCodeEnum.INVALID_TOPIC_ID;
            case INVALID_ADMIN_KEY -> ResponseCodeEnum.INVALID_ADMIN_KEY;
            case INVALID_SUBMIT_KEY -> ResponseCodeEnum.INVALID_SUBMIT_KEY;
            case UNAUTHORIZED -> ResponseCodeEnum.UNAUTHORIZED;
            case INVALID_TOPIC_MESSAGE -> ResponseCodeEnum.INVALID_TOPIC_MESSAGE;
            case INVALID_AUTORENEW_ACCOUNT -> ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
            case AUTORENEW_ACCOUNT_NOT_ALLOWED -> ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
            case TOPIC_EXPIRED -> ResponseCodeEnum.TOPIC_EXPIRED;
            case INVALID_CHUNK_NUMBER -> ResponseCodeEnum.INVALID_CHUNK_NUMBER;
            case INVALID_CHUNK_TRANSACTION_ID -> ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
            case ACCOUNT_FROZEN_FOR_TOKEN -> ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
            case TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED -> ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
            case INVALID_TOKEN_ID -> ResponseCodeEnum.INVALID_TOKEN_ID;
            case INVALID_TOKEN_DECIMALS -> ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
            case INVALID_TOKEN_INITIAL_SUPPLY -> ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
            case INVALID_TREASURY_ACCOUNT_FOR_TOKEN -> ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
            case INVALID_TOKEN_SYMBOL -> ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
            case TOKEN_HAS_NO_FREEZE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
            case TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN -> ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
            case MISSING_TOKEN_SYMBOL -> ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
            case TOKEN_SYMBOL_TOO_LONG -> ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
            case ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN -> ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
            case TOKEN_HAS_NO_KYC_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
            case INSUFFICIENT_TOKEN_BALANCE -> ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
            case TOKEN_WAS_DELETED -> ResponseCodeEnum.TOKEN_WAS_DELETED;
            case TOKEN_HAS_NO_SUPPLY_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
            case TOKEN_HAS_NO_WIPE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
            case INVALID_TOKEN_MINT_AMOUNT -> ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
            case INVALID_TOKEN_BURN_AMOUNT -> ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
            case TOKEN_NOT_ASSOCIATED_TO_ACCOUNT -> ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
            case CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT -> ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
            case INVALID_KYC_KEY -> ResponseCodeEnum.INVALID_KYC_KEY;
            case INVALID_WIPE_KEY -> ResponseCodeEnum.INVALID_WIPE_KEY;
            case INVALID_FREEZE_KEY -> ResponseCodeEnum.INVALID_FREEZE_KEY;
            case INVALID_SUPPLY_KEY -> ResponseCodeEnum.INVALID_SUPPLY_KEY;
            case MISSING_TOKEN_NAME -> ResponseCodeEnum.MISSING_TOKEN_NAME;
            case TOKEN_NAME_TOO_LONG -> ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
            case INVALID_WIPING_AMOUNT -> ResponseCodeEnum.INVALID_WIPING_AMOUNT;
            case TOKEN_IS_IMMUTABLE -> ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
            case TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT -> ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
            case TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES -> ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
            case ACCOUNT_IS_TREASURY -> ResponseCodeEnum.ACCOUNT_IS_TREASURY;
            case TOKEN_ID_REPEATED_IN_TOKEN_LIST -> ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
            case TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
            case EMPTY_TOKEN_TRANSFER_BODY -> ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_BODY;
            case EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS -> ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
            case INVALID_SCHEDULE_ID -> ResponseCodeEnum.INVALID_SCHEDULE_ID;
            case SCHEDULE_IS_IMMUTABLE -> ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
            case INVALID_SCHEDULE_PAYER_ID -> ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
            case INVALID_SCHEDULE_ACCOUNT_ID -> ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
            case NO_NEW_VALID_SIGNATURES -> ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
            case UNRESOLVABLE_REQUIRED_SIGNERS -> ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
            case SCHEDULED_TRANSACTION_NOT_IN_WHITELIST -> ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
            case SOME_SIGNATURES_WERE_INVALID -> ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
            case TRANSACTION_ID_FIELD_NOT_ALLOWED -> ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
            case IDENTICAL_SCHEDULE_ALREADY_CREATED -> ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
            case INVALID_ZERO_BYTE_IN_STRING -> ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
            case SCHEDULE_ALREADY_DELETED -> ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
            case SCHEDULE_ALREADY_EXECUTED -> ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
            case MESSAGE_SIZE_TOO_LARGE -> ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
            case OPERATION_REPEATED_IN_BUCKET_GROUPS -> ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS;
            case BUCKET_CAPACITY_OVERFLOW -> ResponseCodeEnum.BUCKET_CAPACITY_OVERFLOW;
            case NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION -> ResponseCodeEnum
                    .NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION;
            case BUCKET_HAS_NO_THROTTLE_GROUPS -> ResponseCodeEnum.BUCKET_HAS_NO_THROTTLE_GROUPS;
            case THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC -> ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC;
            case SUCCESS_BUT_MISSING_EXPECTED_OPERATION -> ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
            case UNPARSEABLE_THROTTLE_DEFINITIONS -> ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS;
            case INVALID_THROTTLE_DEFINITIONS -> ResponseCodeEnum.INVALID_THROTTLE_DEFINITIONS;
            case ACCOUNT_EXPIRED_AND_PENDING_REMOVAL -> ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
            case INVALID_TOKEN_MAX_SUPPLY -> ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
            case INVALID_TOKEN_NFT_SERIAL_NUMBER -> ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
            case INVALID_NFT_ID -> ResponseCodeEnum.INVALID_NFT_ID;
            case METADATA_TOO_LONG -> ResponseCodeEnum.METADATA_TOO_LONG;
            case BATCH_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
            case INVALID_QUERY_RANGE -> ResponseCodeEnum.INVALID_QUERY_RANGE;
            case FRACTION_DIVIDES_BY_ZERO -> ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
            case INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE -> ResponseCodeEnum
                    .INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
            case CUSTOM_FEES_LIST_TOO_LONG -> ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
            case INVALID_CUSTOM_FEE_COLLECTOR -> ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
            case INVALID_TOKEN_ID_IN_CUSTOM_FEES -> ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
            case TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR -> ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
            case TOKEN_MAX_SUPPLY_REACHED -> ResponseCodeEnum.TOKEN_MAX_SUPPLY_REACHED;
            case SENDER_DOES_NOT_OWN_NFT_SERIAL_NO -> ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
            case CUSTOM_FEE_NOT_FULLY_SPECIFIED -> ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
            case CUSTOM_FEE_MUST_BE_POSITIVE -> ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
            case TOKEN_HAS_NO_FEE_SCHEDULE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
            case CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE -> ResponseCodeEnum.CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
            case ROYALTY_FRACTION_CANNOT_EXCEED_ONE -> ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;
            case FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT -> ResponseCodeEnum
                    .FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT;
            case CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES -> ResponseCodeEnum.CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES;
            case CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON -> ResponseCodeEnum
                    .CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
            case CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON -> ResponseCodeEnum
                    .CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
            case INVALID_CUSTOM_FEE_SCHEDULE_KEY -> ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
            case INVALID_TOKEN_MINT_METADATA -> ResponseCodeEnum.INVALID_TOKEN_MINT_METADATA;
            case INVALID_TOKEN_BURN_METADATA -> ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
            case CURRENT_TREASURY_STILL_OWNS_NFTS -> ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
            case ACCOUNT_STILL_OWNS_NFTS -> ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
            case TREASURY_MUST_OWN_BURNED_NFT -> ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
            case ACCOUNT_DOES_NOT_OWN_WIPED_NFT -> ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT;
            case ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON -> ResponseCodeEnum
                    .ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
            case MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED -> ResponseCodeEnum
                    .MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED;
            case PAYER_ACCOUNT_DELETED -> ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
            case CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH -> ResponseCodeEnum
                    .CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
            case CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS -> ResponseCodeEnum
                    .CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
            case INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE -> ResponseCodeEnum
                    .INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
            case SERIAL_NUMBER_LIMIT_REACHED -> ResponseCodeEnum.SERIAL_NUMBER_LIMIT_REACHED;
            case CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE -> ResponseCodeEnum
                    .CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
            case NO_REMAINING_AUTOMATIC_ASSOCIATIONS -> ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
            case EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT -> ResponseCodeEnum
                    .EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
            case REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT -> ResponseCodeEnum
                    .REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
            case TOKEN_IS_PAUSED -> ResponseCodeEnum.TOKEN_IS_PAUSED;
            case TOKEN_HAS_NO_PAUSE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
            case INVALID_PAUSE_KEY -> ResponseCodeEnum.INVALID_PAUSE_KEY;
            case FREEZE_UPDATE_FILE_DOES_NOT_EXIST -> ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
            case FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH -> ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
            case NO_UPGRADE_HAS_BEEN_PREPARED -> ResponseCodeEnum.NO_UPGRADE_HAS_BEEN_PREPARED;
            case NO_FREEZE_IS_SCHEDULED -> ResponseCodeEnum.NO_FREEZE_IS_SCHEDULED;
            case UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE -> ResponseCodeEnum
                    .UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE;
            case FREEZE_START_TIME_MUST_BE_FUTURE -> ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
            case PREPARED_UPDATE_FILE_IS_IMMUTABLE -> ResponseCodeEnum.PREPARED_UPDATE_FILE_IS_IMMUTABLE;
            case FREEZE_ALREADY_SCHEDULED -> ResponseCodeEnum.FREEZE_ALREADY_SCHEDULED;
            case FREEZE_UPGRADE_IN_PROGRESS -> ResponseCodeEnum.FREEZE_UPGRADE_IN_PROGRESS;
            case UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED -> ResponseCodeEnum.UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED;
            case UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED -> ResponseCodeEnum.UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED;
            case CONSENSUS_GAS_EXHAUSTED -> ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
            case REVERTED_SUCCESS -> ResponseCodeEnum.REVERTED_SUCCESS;
            case MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED -> ResponseCodeEnum
                    .MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
            case INVALID_ALIAS_KEY -> ResponseCodeEnum.INVALID_ALIAS_KEY;
            case UNEXPECTED_TOKEN_DECIMALS -> ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
            case INVALID_PROXY_ACCOUNT_ID -> ResponseCodeEnum.INVALID_PROXY_ACCOUNT_ID;
            case INVALID_TRANSFER_ACCOUNT_ID -> ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
            case INVALID_FEE_COLLECTOR_ACCOUNT_ID -> ResponseCodeEnum.INVALID_FEE_COLLECTOR_ACCOUNT_ID;
            case ALIAS_IS_IMMUTABLE -> ResponseCodeEnum.ALIAS_IS_IMMUTABLE;
            case SPENDER_ACCOUNT_SAME_AS_OWNER -> ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
            case AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY -> ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
            case NEGATIVE_ALLOWANCE_AMOUNT -> ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
            case CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON -> ResponseCodeEnum.CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON;
            case SPENDER_DOES_NOT_HAVE_ALLOWANCE -> ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
            case AMOUNT_EXCEEDS_ALLOWANCE -> ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
            case MAX_ALLOWANCES_EXCEEDED -> ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
            case EMPTY_ALLOWANCES -> ResponseCodeEnum.EMPTY_ALLOWANCES;
            case SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES -> ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
            case REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES -> ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
            case FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES -> ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
            case NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES -> ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
            case INVALID_ALLOWANCE_OWNER_ID -> ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
            case INVALID_ALLOWANCE_SPENDER_ID -> ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
            case REPEATED_ALLOWANCES_TO_DELETE -> ResponseCodeEnum.REPEATED_ALLOWANCES_TO_DELETE;
            case INVALID_DELEGATING_SPENDER -> ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
            case DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL -> ResponseCodeEnum
                    .DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
            case DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL -> ResponseCodeEnum
                    .DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
            case SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE -> ResponseCodeEnum
                    .SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE;
            case SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME -> ResponseCodeEnum
                    .SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME;
            case SCHEDULE_FUTURE_THROTTLE_EXCEEDED -> ResponseCodeEnum.SCHEDULE_FUTURE_THROTTLE_EXCEEDED;
            case SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED -> ResponseCodeEnum.SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
            case INVALID_ETHEREUM_TRANSACTION -> ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
            case WRONG_CHAIN_ID -> ResponseCodeEnum.WRONG_CHAIN_ID;
            case WRONG_NONCE -> ResponseCodeEnum.WRONG_NONCE;
            case ACCESS_LIST_UNSUPPORTED -> ResponseCodeEnum.ACCESS_LIST_UNSUPPORTED;
            case SCHEDULE_PENDING_EXPIRATION -> ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
            case CONTRACT_IS_TOKEN_TREASURY -> ResponseCodeEnum.CONTRACT_IS_TOKEN_TREASURY;
            case CONTRACT_HAS_NON_ZERO_TOKEN_BALANCES -> ResponseCodeEnum.CONTRACT_HAS_NON_ZERO_TOKEN_BALANCES;
            case CONTRACT_EXPIRED_AND_PENDING_REMOVAL -> ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
            case CONTRACT_HAS_NO_AUTO_RENEW_ACCOUNT -> ResponseCodeEnum.CONTRACT_HAS_NO_AUTO_RENEW_ACCOUNT;
            case PERMANENT_REMOVAL_REQUIRES_SYSTEM_INITIATION -> ResponseCodeEnum
                    .PERMANENT_REMOVAL_REQUIRES_SYSTEM_INITIATION;
            case PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED -> ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
            case SELF_STAKING_IS_NOT_ALLOWED -> ResponseCodeEnum.SELF_STAKING_IS_NOT_ALLOWED;
            case INVALID_STAKING_ID -> ResponseCodeEnum.INVALID_STAKING_ID;
            case STAKING_NOT_ENABLED -> ResponseCodeEnum.STAKING_NOT_ENABLED;
            case INVALID_PRNG_RANGE -> ResponseCodeEnum.INVALID_PRNG_RANGE;
            case MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED -> ResponseCodeEnum
                    .MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
            case INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE -> ResponseCodeEnum
                    .INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
            case INSUFFICIENT_BALANCES_FOR_STORAGE_RENT -> ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_STORAGE_RENT;
            case MAX_CHILD_RECORDS_EXCEEDED -> ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
            case INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES -> ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES;
            case TRANSACTION_HAS_UNKNOWN_FIELDS -> ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
            case TOKEN_REFERENCE_REPEATED -> ResponseCodeEnum.TOKEN_REFERENCE_REPEATED;
            case TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED;
            case INVALID_OWNER_ID -> ResponseCodeEnum.INVALID_OWNER_ID;
            case ACCOUNT_IS_IMMUTABLE -> ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
            case ALIAS_ALREADY_ASSIGNED -> ResponseCodeEnum.ALIAS_ALREADY_ASSIGNED;
            case INVALID_METADATA_KEY -> ResponseCodeEnum.INVALID_METADATA_KEY;
            case MISSING_TOKEN_METADATA -> ResponseCodeEnum.MISSING_TOKEN_METADATA;
            case TOKEN_HAS_NO_METADATA_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_METADATA_KEY;
            case MISSING_SERIAL_NUMBERS -> ResponseCodeEnum.MISSING_SERIAL_NUMBERS;
            case TOKEN_HAS_NO_ADMIN_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_ADMIN_KEY;
            case NODE_DELETED -> ResponseCodeEnum.NODE_DELETED;
            case INVALID_NODE_ID -> ResponseCodeEnum.INVALID_NODE_ID;
            case INVALID_GOSSIP_ENDPOINT -> ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT;
            case INVALID_NODE_ACCOUNT_ID -> ResponseCodeEnum.INVALID_NODE_ACCOUNT_ID;
            case INVALID_NODE_DESCRIPTION -> ResponseCodeEnum.INVALID_NODE_DESCRIPTION;
            case INVALID_SERVICE_ENDPOINT -> ResponseCodeEnum.INVALID_SERVICE_ENDPOINT;
            case INVALID_GOSSIP_CA_CERTIFICATE -> ResponseCodeEnum.INVALID_GOSSIP_CA_CERTIFICATE;
            case INVALID_GRPC_CERTIFICATE -> ResponseCodeEnum.INVALID_GRPC_CERTIFICATE;
            case INVALID_MAX_AUTO_ASSOCIATIONS -> ResponseCodeEnum.INVALID_MAX_AUTO_ASSOCIATIONS;
            case MAX_NODES_CREATED -> ResponseCodeEnum.MAX_NODES_CREATED;
            case IP_FQDN_CANNOT_BE_SET_FOR_SAME_ENDPOINT -> ResponseCodeEnum.IP_FQDN_CANNOT_BE_SET_FOR_SAME_ENDPOINT;
            case GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN -> ResponseCodeEnum.GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN;
            case FQDN_SIZE_TOO_LARGE -> ResponseCodeEnum.FQDN_SIZE_TOO_LARGE;
            case INVALID_ENDPOINT -> ResponseCodeEnum.INVALID_ENDPOINT;
            case GOSSIP_ENDPOINTS_EXCEEDED_LIMIT -> ResponseCodeEnum.GOSSIP_ENDPOINTS_EXCEEDED_LIMIT;
            case SERVICE_ENDPOINTS_EXCEEDED_LIMIT -> ResponseCodeEnum.SERVICE_ENDPOINTS_EXCEEDED_LIMIT;
            case INVALID_IPV4_ADDRESS -> ResponseCodeEnum.INVALID_IPV4_ADDRESS;
            case EMPTY_TOKEN_REFERENCE_LIST -> ResponseCodeEnum.EMPTY_TOKEN_REFERENCE_LIST;
            case UPDATE_NODE_ACCOUNT_NOT_ALLOWED -> ResponseCodeEnum.UPDATE_NODE_ACCOUNT_NOT_ALLOWED;
            case UNRECOGNIZED -> throw new RuntimeException("UNRECOGNIZED Response code!");
        };
    }

    public static @NonNull com.hederahashgraph.api.proto.java.ContractID fromPbj(final @NonNull ContractID contractID) {
        requireNonNull(contractID);
        return com.hederahashgraph.api.proto.java.ContractID.newBuilder()
                .setRealmNum(contractID.realmNum())
                .setShardNum(contractID.shardNum())
                .setContractNum(contractID.contractNumOrElse(0L))
                .setEvmAddress(ByteString.copyFrom(asBytes(contractID.evmAddressOrElse(Bytes.EMPTY))))
                .build();
    }

    public static @NonNull ByteString fromPbj(@NonNull Bytes bytes) {
        requireNonNull(bytes);
        final byte[] data = new byte[Math.toIntExact(bytes.length())];
        bytes.getBytes(0, data);
        return ByteString.copyFrom(data);
    }

    public static com.hederahashgraph.api.proto.java.Timestamp fromPbj(@NonNull Timestamp now) {
        requireNonNull(now);
        return com.hederahashgraph.api.proto.java.Timestamp.newBuilder()
                .setSeconds(now.seconds())
                .setNanos(now.nanos())
                .build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.ScheduleInfo fromPbj(@NonNull ScheduleInfo pbjValue) {
        requireNonNull(pbjValue);
        try {
            final var bytes = asBytes(ScheduleInfo.PROTOBUF, pbjValue);
            return com.hederahashgraph.api.proto.java.ScheduleInfo.parseFrom(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.FeeData fromPbj(@NonNull FeeData feeData) {
        requireNonNull(feeData);
        return com.hederahashgraph.api.proto.java.FeeData.newBuilder()
                .setNodedata(fromPbj(feeData.nodedataOrElse(FeeComponents.DEFAULT)))
                .setNetworkdata(fromPbj(feeData.networkdataOrElse(FeeComponents.DEFAULT)))
                .setServicedata(fromPbj(feeData.servicedataOrElse(FeeComponents.DEFAULT)))
                .setSubTypeValue(feeData.subType().protoOrdinal())
                .build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.FeeComponents fromPbj(
            @NonNull FeeComponents feeComponents) {
        requireNonNull(feeComponents);
        return com.hederahashgraph.api.proto.java.FeeComponents.newBuilder()
                .setMin(feeComponents.min())
                .setMax(feeComponents.max())
                .setConstant(feeComponents.constant())
                .setBpt(feeComponents.bpt())
                .setVpt(feeComponents.vpt())
                .setRbh(feeComponents.rbh())
                .setSbh(feeComponents.sbh())
                .setGas(feeComponents.gas())
                .setTv(feeComponents.tv())
                .setBpr(feeComponents.bpr())
                .setSbpr(feeComponents.sbpr())
                .build();
    }

    public static FileID toPbj(com.hederahashgraph.api.proto.java.FileID fileID) {
        return protoToPbj(fileID, FileID.class);
    }

    public static @NonNull com.hederahashgraph.api.proto.java.AccountID fromPbj(@NonNull AccountID accountID) {
        requireNonNull(accountID);
        final var builder = com.hederahashgraph.api.proto.java.AccountID.newBuilder()
                .setShardNum(accountID.shardNum())
                .setRealmNum(accountID.realmNum());

        final var account = accountID.account();
        switch (account.kind()) {
            case ACCOUNT_NUM -> builder.setAccountNum(account.as());
            case ALIAS -> builder.setAlias(fromPbj((Bytes) account.as()));
            case UNSET -> throw new RuntimeException("Invalid account ID, no account type!");
        }

        return builder.build();
    }

    public static @NonNull Key toPbj(@NonNull com.hederahashgraph.api.proto.java.Key keyValue) {
        requireNonNull(keyValue);
        try {
            final var bytes = keyValue.toByteArray();
            return Key.PROTOBUF.parse(BufferedData.wrap(bytes));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static Timestamp toPbj(@NonNull com.hederahashgraph.api.proto.java.Timestamp t) {
        requireNonNull(t);
        return Timestamp.newBuilder()
                .seconds(t.getSeconds())
                .nanos(t.getNanos())
                .build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.ExchangeRate fromPbj(@NonNull ExchangeRate exchangeRate) {
        return com.hederahashgraph.api.proto.java.ExchangeRate.newBuilder()
                .setCentEquiv(exchangeRate.centEquiv())
                .setHbarEquiv(exchangeRate.hbarEquiv())
                .build();
    }

    public static @NonNull TransactionBody toPbj(@NonNull com.hederahashgraph.api.proto.java.TransactionBody txBody) {
        requireNonNull(txBody);
        try {
            final var bytes = txBody.toByteArray();
            return TransactionBody.PROTOBUF.parse(BufferedData.wrap(bytes));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull byte[] asBytes(@NonNull BufferedData b) {
        final var buf = new byte[Math.toIntExact(b.position())];
        b.readBytes(buf);
        return buf;
    }

    public static @NonNull com.hederahashgraph.api.proto.java.SchedulableTransactionBody fromPbj(
            @NonNull SchedulableTransactionBody tx) {
        requireNonNull(tx);
        try {
            final var bytes = asBytes(SchedulableTransactionBody.PROTOBUF, tx);
            return com.hederahashgraph.api.proto.java.SchedulableTransactionBody.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts a {@link ByteString} to a {@link Bytes} object.
     * @param contents The {@link ByteString} to convert.
     * @return The {@link Bytes} object.
     */
    public static Bytes fromByteString(ByteString contents) {
        return Bytes.wrap(unwrapUnsafelyIfPossible(contents));
    }

    public static ServiceEndpoint toPbj(@NonNull com.hederahashgraph.api.proto.java.ServiceEndpoint t) {
        requireNonNull(t);
        return ServiceEndpoint.newBuilder()
                .ipAddressV4(Bytes.wrap(t.getIpAddressV4().toByteArray()))
                .port(t.getPort())
                .domainName(t.getDomainName())
                .build();
    }
}
