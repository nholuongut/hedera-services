/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip869;

import static com.hedera.services.bdd.junit.EmbeddedReason.MUST_SKIP_INGEST;
import static com.hedera.services.bdd.junit.EmbeddedReason.NEEDS_STATE_ACCESS;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.viewNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NODE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.bdd.junit.EmbeddedHapiTest;
import com.hedera.services.bdd.junit.HapiTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class NodeDeleteTest {
    @EmbeddedHapiTest(NEEDS_STATE_ACCESS)
    final Stream<DynamicTest> deleteNodeWorks() {
        final String nodeName = "mytestnode";

        return hapiTest(
                nodeCreate(nodeName),
                viewNode(nodeName, node -> assertFalse(node.deleted(), "Node should not be deleted")),
                nodeDelete(nodeName),
                viewNode(nodeName, node -> assertTrue(node.deleted(), "Node should be deleted")));
    }

    @EmbeddedHapiTest(MUST_SKIP_INGEST)
    final Stream<DynamicTest> validateFees() {
        final String description = "His vorpal blade went snicker-snack!";
        return defaultHapiSpec("validateFees")
                .given(
                        newKeyNamed("testKey"),
                        newKeyNamed("randomAccount"),
                        cryptoCreate("payer").balance(10_000_000_000L),
                        nodeCreate("node100").description(description).fee(ONE_HBAR),
                        // Submit to a different node so ingest check is skipped
                        nodeDelete("node100")
                                .setNode("0.0.5")
                                .payingWith("payer")
                                .hasKnownStatus(UNAUTHORIZED)
                                .via("failedDeletion"))
                .when()
                .then(
                        getTxnRecord("failedDeletion").logged(),
                        // The fee is charged here because the payer is not privileged
                        validateChargedUsdWithin("failedDeletion", 0.001, 3.0),

                        // Submit with several signatures and the price should increase
                        nodeDelete("node100")
                                .setNode("0.0.5")
                                .payingWith("payer")
                                .signedBy("payer", "randomAccount", "testKey")
                                .hasKnownStatus(UNAUTHORIZED)
                                .via("multipleSigsDeletion"),
                        validateChargedUsdWithin("multipleSigsDeletion", 0.0011276316, 3.0),
                        nodeDelete("node100").via("deleteNode"),
                        getTxnRecord("deleteNode").logged(),
                        // The fee is not charged here because the payer is privileged
                        validateChargedUsdWithin("deleteNode", 0.0, 3.0));
    }

    @EmbeddedHapiTest(MUST_SKIP_INGEST)
    final Stream<DynamicTest> validateFeesInsufficientAmount() {
        final String description = "His vorpal blade went snicker-snack!";
        return hapiTest(
                newKeyNamed("testKey"),
                newKeyNamed("randomAccount"),
                cryptoCreate("payer").balance(10_000_000_000L),
                nodeCreate("node100").description(description).fee(ONE_HBAR),
                // Submit to a different node so ingest check is skipped
                nodeDelete("node100")
                        .setNode("0.0.5")
                        .fee(1)
                        .payingWith("payer")
                        .hasKnownStatus(INSUFFICIENT_TX_FEE)
                        .via("failedDeletion"),
                getTxnRecord("failedDeletion").logged(),
                // Submit with several signatures and the price should increase
                nodeDelete("node100")
                        .setNode("0.0.5")
                        .fee(ONE_HBAR)
                        .payingWith("payer")
                        .signedBy("payer", "randomAccount", "testKey")
                        .hasKnownStatus(UNAUTHORIZED)
                        .via("multipleSigsDeletion"),
                nodeDelete("node100").via("deleteNode"),
                getTxnRecord("deleteNode").logged());
    }

    @HapiTest
    final Stream<DynamicTest> failsAtIngestForUnAuthorizedTxns() {
        final String description = "His vorpal blade went snicker-snack!";
        return defaultHapiSpec("failsAtIngestForUnAuthorizedTxns")
                .given(
                        cryptoCreate("payer").balance(10_000_000_000L),
                        nodeCreate("ntb").description(description).fee(ONE_HBAR),
                        nodeDelete("ntb")
                                .payingWith("payer")
                                .fee(ONE_HBAR)
                                .hasPrecheck(BUSY)
                                .via("failedDeletion"))
                .when()
                .then();
    }

    @HapiTest
    final Stream<DynamicTest> handleNodeNotExist() {
        final String nodeName = "33";
        return hapiTest(nodeDelete(nodeName).hasKnownStatus(INVALID_NODE_ID));
    }

    @HapiTest
    final Stream<DynamicTest> handleNodeAlreadyDeleted() {
        final String nodeName = "mytestnode";
        return hapiTest(
                nodeCreate(nodeName),
                nodeDelete(nodeName),
                nodeDelete(nodeName).signedBy(GENESIS).hasKnownStatus(NODE_DELETED));
    }

    @HapiTest
    final Stream<DynamicTest> handleCanBeExecutedJustWithPrivilegedAccount() {
        long PAYER_BALANCE = 1_999_999_999L;
        final String nodeName = "mytestnode";

        return hapiTest(
                newKeyNamed("adminKey"),
                newKeyNamed("wrongKey"),
                cryptoCreate("payer").balance(PAYER_BALANCE).key("wrongKey"),
                nodeCreate(nodeName),
                nodeDelete(nodeName)
                        .payingWith("payer")
                        .signedBy("payer", "wrongKey")
                        .hasPrecheck(BUSY),
                nodeDelete(nodeName));
    }
}
