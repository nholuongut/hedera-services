/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.staking;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.HBARS_TO_TINYBARS;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUtils.calculateRewardSumHistory;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUtils.computeNextStake;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUtils.readableNonZeroHistory;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.transactionWith;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.transaction.NodeStake;
import com.hedera.hapi.node.transaction.NodeStakeUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.records.NodeStakeUpdateRecordBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.config.data.StakingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Updates the stake and reward values for all nodes at the end of a staking period.
 */
@Singleton
public class EndOfStakingPeriodUpdater {
    private static final Logger log = LogManager.getLogger(EndOfStakingPeriodUpdater.class);

    // The exact choice of precision will not have a large effect on the per-hbar reward rate
    private static final MathContext MATH_CONTEXT = new MathContext(8, RoundingMode.DOWN);

    private final HederaAccountNumbers accountNumbers;
    private final StakingRewardsHelper stakeRewardsHelper;

    /**
     * Constructs an {@link EndOfStakingPeriodUpdater} instance.
     * @param accountNumbers the account numbers
     * @param stakeRewardsHelper the staking rewards helper
     */
    @Inject
    public EndOfStakingPeriodUpdater(
            @NonNull final HederaAccountNumbers accountNumbers,
            @NonNull final StakingRewardsHelper stakeRewardsHelper) {
        this.accountNumbers = accountNumbers;
        this.stakeRewardsHelper = stakeRewardsHelper;
    }

    /**
     * Updates all (relevant) staking-related values for all nodes, as well as any network reward information,
     * at the end of a staking period. This method must be invoked during handling of a transaction
     *
     * @param context the context of the transaction used to end the staking period
     */
    public void updateNodes(@NonNull final TokenContext context) {
        final var consensusTime = context.consensusTime();
        log.info("Updating node stakes for a just-finished period @ {}", consensusTime);

        // First, determine if staking is enabled. If not, there is nothing to do
        final var stakingConfig = context.configuration().getConfigData(StakingConfig.class);
        if (!stakingConfig.isEnabled()) {
            log.info("Staking not enabled, nothing to do");
            return;
        }

        final ReadableAccountStore accountStore = context.readableStore(ReadableAccountStore.class);
        final WritableStakingInfoStore stakingInfoStore = context.writableStore(WritableStakingInfoStore.class);
        final WritableNetworkStakingRewardsStore stakingRewardsStore =
                context.writableStore(WritableNetworkStakingRewardsStore.class);

        final var nodeIds = context.knownNodeIds();
        final var totalStakedRewardStart = stakingRewardsStore.totalStakeRewardStart();
        final var rewardRate = perHbarRewardRateForEndingPeriod(
                totalStakedRewardStart, accountStore, stakingRewardsStore, stakingConfig);
        // The tinybars earned per hbar for stakers who were staked to a node whose total
        // stakedRewardStart for the ending period was in the range [minStake, maxStake]
        // plus a boundary-case check for zero whole hbars staked
        final var perHbarRate = totalStakedRewardStart < HBARS_TO_TINYBARS ? 0 : rewardRate;
        log.info(
                "The reward rate for the period was {} tb ({} tb/hbar for nodes with in-range stake, given {} total stake reward start)",
                rewardRate,
                perHbarRate,
                totalStakedRewardStart);

        // Calculate the updated stake and reward sum history for each node
        long newTotalStakedStart = 0L;
        long newTotalStakedRewardStart = 0L;
        long maxStakeOfAllNodes = 0L;
        final Map<Long, StakingNodeInfo> updatedNodeInfos = new HashMap<>();
        final Map<Long, Long> newPendingRewardRates = new HashMap<>();
        for (final var nodeNum : nodeIds.stream().sorted().toList()) {
            var currStakingInfo = requireNonNull(stakingInfoStore.getForModify(nodeNum));

            // The return value here includes both the new reward sum history, and the reward rate
            // (tinybars-per-hbar-staked-to-reward) that will be paid to all accounts who had staked-to-reward for this
            // node long enough to be eligible in the just-finished period
            final var newRewardSumHistory = calculateRewardSumHistory(
                    currStakingInfo,
                    perHbarRate,
                    stakingConfig.perHbarRewardRate(),
                    stakingConfig.requireMinStakeToReward());
            final var newPendingRewardRate = newRewardSumHistory.pendingRewardRate();
            newPendingRewardRates.put(nodeNum, newPendingRewardRate);
            currStakingInfo = currStakingInfo
                    .copyBuilder()
                    .rewardSumHistory(newRewardSumHistory.rewardSumHistory())
                    .build();
            log.info(
                    "Non-zero reward sum history for node number {} is now {}",
                    () -> nodeNum,
                    () -> readableNonZeroHistory(newRewardSumHistory.rewardSumHistory()));

            final var oldStakeRewardStart = currStakingInfo.stakeRewardStart();
            final var pendingRewardHbars =
                    (oldStakeRewardStart - currStakingInfo.unclaimedStakeRewardStart()) / HBARS_TO_TINYBARS;
            final var recomputedStake = computeNextStake(currStakingInfo);
            currStakingInfo = currStakingInfo
                    .copyBuilder()
                    .stake(recomputedStake.stake())
                    .stakeRewardStart(recomputedStake.stakeRewardStart())
                    .unclaimedStakeRewardStart(0)
                    .build();
            final var newStakeRewardStart = recomputedStake.stakeRewardStart();
            final var nodePendingRewards = pendingRewardHbars * newPendingRewardRate;
            log.info(
                    "For node{}, the tb/hbar reward rate was {} for {} pending, with stake reward start {} -> {}",
                    nodeNum,
                    newPendingRewardRate,
                    nodePendingRewards,
                    oldStakeRewardStart,
                    newStakeRewardStart);
            currStakingInfo = stakeRewardsHelper.increasePendingRewardsBy(
                    stakingRewardsStore, nodePendingRewards, currStakingInfo);

            newTotalStakedRewardStart += newStakeRewardStart;
            newTotalStakedStart += currStakingInfo.stake();
            // Update the max stake of all nodes
            maxStakeOfAllNodes = Math.max(maxStakeOfAllNodes, currStakingInfo.stake());
            // Keep the staking node info objects because we'll need them for more calculations
            updatedNodeInfos.put(nodeNum, currStakingInfo);
        }

        // Update node stake infos for the record with the updated consensus weights. The weights are updated based on
        // the updated stake of the node.
        final var finalNodeStakes = new ArrayList<NodeStake>();
        final var sumOfConsensusWeights = stakingConfig.sumOfConsensusWeights();
        for (final Map.Entry<Long, StakingNodeInfo> entry : updatedNodeInfos.entrySet()) {
            final var nodeNum = entry.getKey();
            var stakingInfo = entry.getValue();

            // If the total stake(rewarded + non-rewarded) of a node is less than minStake, stakingInfo's stake field
            // represents 0, as per calculation done in reviewElectionsAndRecomputeStakes. Similarly, the total
            // stake(rewarded + non-rewarded) of the node is greater than maxStake, stakingInfo's stake field is set to
            // maxStake.So, there is no need to clamp the stake value here. Sum of all stakes can be used to calculate
            // the weight.
            final int updatedWeight;
            if (!stakingInfo.deleted()) {
                updatedWeight =
                        calculateWeightFromStake(stakingInfo.stake(), newTotalStakedStart, sumOfConsensusWeights);
            } else {
                updatedWeight = 0;
            }
            final var oldWeight = stakingInfo.weight();
            stakingInfo = stakingInfo.copyBuilder().weight(updatedWeight).build();
            log.info(
                    "Node {} weight is calculated. Old weight {}, updated weight {}",
                    nodeNum,
                    oldWeight,
                    updatedWeight);

            // Scale the consensus weight range [0, sumOfConsensusWeights] to [minStake, trueMaxStakeOfAllNodes] range
            // and export to mirror node. We need to consider the true maxStakeOfAllNodes instead of maxStake becausefor
            // a one node network, whose stake < maxStake, we assign a weight of sumOfConsensusWeights to the node. When
            // we scale it back to stake, we need to give back the real stake of the node instead of maxStake set on the
            // node.
            final var scaledWeightToStake = scaleUpWeightToStake(
                    updatedWeight,
                    stakingInfo.minStake(),
                    maxStakeOfAllNodes,
                    newTotalStakedStart,
                    sumOfConsensusWeights);
            finalNodeStakes.add(fromStakingInfo(
                    newPendingRewardRates.get(nodeNum),
                    entry.getValue().copyBuilder().stake(scaledWeightToStake).build()));

            // Persist the updated staking info
            stakingInfoStore.put(nodeNum, stakingInfo);
        }

        // Update the staking reward values for the network
        final var newNetworkStakingRewards = copy(stakingRewardsStore)
                .totalStakedRewardStart(newTotalStakedRewardStart)
                .totalStakedStart(newTotalStakedStart);
        stakingRewardsStore.put(newNetworkStakingRewards.build());
        final long rewardAccountBalance = getRewardsBalance(accountStore);
        log.info(
                "Total stake start is now {} ({} rewarded), pending rewards are {} vs 0.0.800" + " balance {}",
                newTotalStakedStart,
                newTotalStakedRewardStart,
                stakingRewardsStore.pendingRewards(),
                rewardAccountBalance);

        // Submit a synthetic node stake update transaction
        final long reservedStakingRewards = stakingRewardsStore.pendingRewards();
        final long unreservedStakingRewardBalance = rewardAccountBalance - reservedStakingRewards;
        final var syntheticNodeStakeUpdateTxn = newNodeStakeUpdateBuilder(
                lastInstantOfPreviousPeriodFor(consensusTime),
                finalNodeStakes,
                stakingConfig,
                totalStakedRewardStart,
                perHbarRate,
                reservedStakingRewards,
                unreservedStakingRewardBalance,
                stakingConfig.rewardBalanceThreshold(),
                stakingConfig.maxStakeRewarded());
        log.info("Exporting:\n{}", finalNodeStakes);
        // We don't want to fail adding the preceding child record for the node stake update that happens every
        // midnight. So, we add the preceding child record builder as unchecked, that doesn't fail with
        // MAX_CHILD_RECORDS_EXCEEDED
        final var nodeStakeUpdateBuilder =
                context.addUncheckedPrecedingChildRecordBuilder(NodeStakeUpdateRecordBuilder.class);
        nodeStakeUpdateBuilder
                .transaction(transactionWith(syntheticNodeStakeUpdateTxn.build()))
                .memo("End of staking period calculation record")
                .status(SUCCESS);
    }

    /**
     * Scales up the weight of the node to the range [minStake, maxStakeOfAllNodes] from the consensus weight range [0, sumOfConsensusWeights].
     *
     * @param weight weight of the node
     * @param newMinStake min stake of the node
     * @param newMaxStake real max stake of all nodes computed by taking max(stakeOfNode1, stakeOfNode2, ...)
     * @param totalStakeOfAllNodes total stake of all nodes at the start of new period
     * @param sumOfConsensusWeights sum of consensus weights of all nodes
     * @return scaled weight of the node
     */
    @VisibleForTesting
    public static long scaleUpWeightToStake(
            final int weight,
            final long newMinStake,
            final long newMaxStake,
            final long totalStakeOfAllNodes,
            final int sumOfConsensusWeights) {
        // Don't scale a weight of zero
        if (weight == 0) {
            return 0;
        }

        if (totalStakeOfAllNodes == 0) {
            // This should never happen, but if it does, return zero
            log.warn(
                    "Total stake of all nodes is 0, which shouldn't happen (weight={}, minStake={}, maxStake={}, sumOfConsensusWeights={})",
                    weight,
                    newMinStake,
                    newMaxStake,
                    sumOfConsensusWeights);
            return 0;
        }

        final var oldMinWeight = 1L;
        // Since we are calculating weights based on the real stake values, we need to consider the real max Stake and
        // not theoretical max stake of nodes. Otherwise, on a one node network with a stake < maxStake where we assign
        // a weight of sumOfConsensusWeights, the scaled stake value will be greater than its real stake.
        final var oldMaxWeight = BigInteger.valueOf(newMaxStake)
                .multiply(BigInteger.valueOf(sumOfConsensusWeights))
                .divide(BigInteger.valueOf(totalStakeOfAllNodes))
                .longValue();
        // Otherwise compute the interpolation of the weight in the range [minStake, maxStake]
        return BigInteger.valueOf(newMaxStake)
                .subtract(BigInteger.valueOf(newMinStake))
                .multiply(BigInteger.valueOf(weight - oldMinWeight))
                .divide(BigInteger.valueOf(oldMaxWeight - oldMinWeight))
                .add(BigInteger.valueOf(newMinStake))
                .longValue();
    }

    /**
     * Calculates consensus weight of the node. The network normalizes the weights of nodes above minStake so that the
     * total sum of weight is approximately as described by {@code StakingConfig#sumOfConsensusWeights}.
     * The stake field in {@code StakingNodeInfo} is already clamped to [minStake, maxStake].
     * If stake is less than minStake the weight of a node A will be 0. If stake is greater than minStake, the weight of a node A
     * will be computed so that every node above minStake has weight at least 1; but any node that has staked at least 1
     * out of every 250 whole hbars staked will have weight >= 2.
     *
     * @param stake the stake of current node, includes stake rewarded and non-rewarded
     * @param totalStakeOfAllNodes the total stake of all nodes at the start of new period
     * @param sumOfConsensusWeights the sum of consensus weights of all nodes
     * @return calculated consensus weight of the node
     */
    @VisibleForTesting
    public static int calculateWeightFromStake(
            final long stake, final long totalStakeOfAllNodes, final int sumOfConsensusWeights) {
        // if node's total stake is less than minStake, the StakingNodeInfo stake will be zero
        if (stake == 0) return 0;

        // If a node's stake is not zero then totalStakeOfAllNodes can't be zero. This error should never happen. It is
        // added to avoid divide by zero exception, in case of any bug.
        if (totalStakeOfAllNodes <= 0L) {
            log.warn("Total stake of all nodes should be greater than 0. But got {}", totalStakeOfAllNodes);
            return 0;
        }
        final var weight = BigInteger.valueOf(stake)
                .multiply(BigInteger.valueOf(sumOfConsensusWeights))
                .divide(BigInteger.valueOf(totalStakeOfAllNodes))
                .longValue();
        return (int) Math.max(weight, 1);
    }

    /**
     * Returns the timestamp that is just before midnight of the day of the given consensus time.
     * @param consensusTime the consensus time
     * @return the timestamp that is just before midnight of the day of the given consensus time
     */
    @VisibleForTesting
    public static Timestamp lastInstantOfPreviousPeriodFor(@NonNull final Instant consensusTime) {
        final var justBeforeMidNightTime = LocalDate.ofInstant(consensusTime, ZoneId.of("UTC"))
                .atStartOfDay()
                .minusNanos(1); // give out the timestamp that is just before midnight
        return Timestamp.newBuilder()
                .seconds(justBeforeMidNightTime.toEpochSecond(ZoneOffset.UTC))
                .nanos(justBeforeMidNightTime.getNano())
                .build();
    }

    /**
     * Given the amount that was staked to reward at the start of the period that is now ending, returns
     * the effective per-hbar reward rate for the period.
     *
     * @param stakedToReward the amount of hbars staked to reward at the start of the ending period
     * @return the effective per-hbar reward rate for the period
     */
    private long perHbarRewardRateForEndingPeriod(
            final long stakedToReward,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableNetworkStakingRewardsStore networkRewardsStore,
            @NonNull final StakingConfig stakingConfig) {
        // The balance left in the rewards account (in tinybars), after paying all rewards earned so far
        final var unreservedBalance = getRewardsBalance(accountStore) - networkRewardsStore.pendingRewards();

        final var thresholdBalance = stakingConfig.rewardBalanceThreshold();
        // A number proportional to the unreserved balance, from 0 for empty, up to 1 at the threshold
        final var balanceRatio = ratioOf(unreservedBalance, thresholdBalance);

        return rescaledPerHbarRewardRate(
                balanceRatio, stakedToReward, stakingConfig.perHbarRewardRate(), stakingConfig.maxStakeRewarded());
    }

    /**
     * Given the {@code 0.0.800} unreserved balance and its threshold setting, returns the ratio of the balance to the
     * threshold, from 0 for empty, up to 1 at the threshold.
     *
     * @param unreservedBalance the balance in {@code 0.0.800} minus the pending rewards
     * @param thresholdBalance the threshold balance setting
     * @return the ratio of the balance to the threshold, from 0 for empty, up to 1 at the threshold
     */
    private BigDecimal ratioOf(final long unreservedBalance, final long thresholdBalance) {
        return thresholdBalance > 0L
                ? BigDecimal.valueOf(Math.min(unreservedBalance, thresholdBalance))
                        .divide(BigDecimal.valueOf(thresholdBalance), MATH_CONTEXT)
                : BigDecimal.ONE;
    }

    /**
     * Given the {@code 0.0.800} balance ratio relative to the threshold, the amount that was staked to reward at the
     * start of the period that is now ending, and the maximum amount of tinybars to pay as staking rewards in the
     * period, returns the effective per-hbar reward rate for the period.
     *
     * @param balanceRatio the ratio of the {@code 0.0.800} balance to the threshold
     * @param stakedToReward the amount of hbars staked to reward at the start of the ending period
     * @param maxRewardRate the maximum amount of tinybars to pay per hbar reward
     * @param maxStakeRewarded the maximum amount of stake that can be rewarded
     * @return the effective per-hbar reward rate for the period
     */
    @VisibleForTesting
    long rescaledPerHbarRewardRate(
            @NonNull final BigDecimal balanceRatio,
            final long stakedToReward,
            final long maxRewardRate,
            final long maxStakeRewarded) {
        // When 0.0.800 has a high balance, and less than maxStakeRewarded is staked for reward, then
        // effectiveRewardRate == rewardRate. But as the balance drops or the staking increases, then
        // it can be the case that effectiveRewardRate < rewardRate
        return BigDecimal.valueOf(maxRewardRate)
                .multiply(balanceRatio.multiply(BigDecimal.valueOf(2).subtract(balanceRatio)))
                .multiply(
                        maxStakeRewarded >= stakedToReward
                                ? BigDecimal.ONE
                                : BigDecimal.valueOf(maxStakeRewarded)
                                        .divide(BigDecimal.valueOf(stakedToReward), MATH_CONTEXT))
                .longValue();
    }

    private long getRewardsBalance(@NonNull final ReadableAccountStore accountStore) {
        return requireNonNull(accountStore.getAccountById(asAccount(accountNumbers.stakingRewardAccount())))
                .tinybarBalance();
    }

    private static NetworkStakingRewards.Builder copy(final ReadableNetworkStakingRewardsStore networkRewardsStore) {
        return NetworkStakingRewards.newBuilder()
                .pendingRewards(networkRewardsStore.pendingRewards())
                .stakingRewardsActivated(networkRewardsStore.isStakingRewardsActivated())
                .totalStakedRewardStart(networkRewardsStore.totalStakeRewardStart())
                .totalStakedStart(networkRewardsStore.totalStakedStart());
    }

    private static NodeStake fromStakingInfo(final long rewardRate, StakingNodeInfo stakingNodeInfo) {
        return NodeStake.newBuilder()
                .nodeId(stakingNodeInfo.nodeNumber())
                .stake(stakingNodeInfo.stake())
                .rewardRate(rewardRate)
                .minStake(stakingNodeInfo.minStake())
                .maxStake(stakingNodeInfo.maxStake())
                .stakeRewarded(stakingNodeInfo.stakeToReward())
                .stakeNotRewarded(stakingNodeInfo.stakeToNotReward())
                .build();
    }

    /**
     * Given information about node stakes and staking reward rates for an ending period, initializes a
     * transaction builder with a {@link NodeStakeUpdateTransactionBody} that summarizes this information.
     *
     * @param stakingPeriodEnd the last nanosecond of the staking period being described
     * @param nodeStakes the stakes of each node at the end of the just-ending period
     * @param stakingConfig the staking configuration of the network at period end
     * @param totalStakedRewardStart the total staked reward at the start of the period
     * @param maxPerHbarRewardRate the maximum reward rate per hbar for the period (per HIP-782)
     * @param reservedStakingRewards the total amount of staking rewards reserved in the 0.0.800 balance
     * @param unreservedStakingRewardBalance the remaining "unreserved" part of the 0.0.800 balance
     * @param rewardBalanceThreshold the 0.0.800 balance threshold at which the max reward rate is attainable
     * @param maxStakeRewarded the maximum stake that can be rewarded at the max reward rate
     * @return the transaction builder with the {@code NodeStakeUpdateTransactionBody} set
     */
    private static TransactionBody.Builder newNodeStakeUpdateBuilder(
            final Timestamp stakingPeriodEnd,
            @NonNull final List<NodeStake> nodeStakes,
            @NonNull final StakingConfig stakingConfig,
            final long totalStakedRewardStart,
            final long maxPerHbarRewardRate,
            final long reservedStakingRewards,
            final long unreservedStakingRewardBalance,
            final long rewardBalanceThreshold,
            final long maxStakeRewarded) {
        final var threshold = stakingConfig.startThreshold();
        final var stakingPeriod = stakingConfig.periodMins();
        final var stakingPeriodsStored = stakingConfig.rewardHistoryNumStoredPeriods();

        final var nodeRewardFeeFraction = Fraction.newBuilder()
                .numerator(stakingConfig.feesNodeRewardPercentage())
                .denominator(100L)
                .build();
        final var stakingRewardFeeFraction = Fraction.newBuilder()
                .numerator(stakingConfig.feesStakingRewardPercentage())
                .denominator(100L)
                .build();

        final var hbarsStakedToReward = (totalStakedRewardStart / HBARS_TO_TINYBARS);
        final var maxTotalReward = maxPerHbarRewardRate * hbarsStakedToReward;
        final var txnBody = NodeStakeUpdateTransactionBody.newBuilder()
                .endOfStakingPeriod(stakingPeriodEnd)
                .nodeStake(nodeStakes)
                .maxStakingRewardRatePerHbar(maxPerHbarRewardRate)
                .nodeRewardFeeFraction(nodeRewardFeeFraction)
                .stakingPeriodsStored(stakingPeriodsStored)
                .stakingPeriod(stakingPeriod)
                .stakingRewardFeeFraction(stakingRewardFeeFraction)
                .stakingStartThreshold(threshold)
                // Deprecated field but keep it for backward compatibility at the moment
                .stakingRewardRate(maxTotalReward)
                .maxTotalReward(maxTotalReward)
                .reservedStakingRewards(reservedStakingRewards)
                .unreservedStakingRewardBalance(unreservedStakingRewardBalance)
                .rewardBalanceThreshold(rewardBalanceThreshold)
                .maxStakeRewarded(maxStakeRewarded)
                .build();

        return TransactionBody.newBuilder().nodeStakeUpdate(txnBody);
    }
}
