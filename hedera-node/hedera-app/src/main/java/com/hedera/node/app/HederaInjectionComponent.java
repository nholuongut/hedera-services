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

package com.hedera.node.app;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.annotations.MaxSignedTxnSize;
import com.hedera.node.app.authorization.AuthorizerInjectionModule;
import com.hedera.node.app.components.IngestInjectionComponent;
import com.hedera.node.app.components.QueryInjectionComponent;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.grpc.GrpcInjectionModule;
import com.hedera.node.app.grpc.GrpcServerManager;
import com.hedera.node.app.info.CurrentPlatformStatus;
import com.hedera.node.app.info.InfoInjectionModule;
import com.hedera.node.app.metrics.MetricsInjectionModule;
import com.hedera.node.app.platform.PlatformModule;
import com.hedera.node.app.records.BlockRecordInjectionModule;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.services.ServicesInjectionModule;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.state.HederaStateInjectionModule;
import com.hedera.node.app.state.PlatformStateAccessor;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.throttle.ThrottleServiceModule;
import com.hedera.node.app.workflows.WorkflowsInjectionModule;
import com.hedera.node.app.workflows.handle.HandleWorkflow;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.SelfNodeInfo;
import dagger.BindsInstance;
import dagger.Component;
import java.nio.charset.Charset;
import java.time.InstantSource;
import java.util.function.Supplier;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * The infrastructure used to implement the platform contract for a Hedera Services node.
 */
@Singleton
@Component(
        modules = {
            ServicesInjectionModule.class,
            WorkflowsInjectionModule.class,
            HederaStateInjectionModule.class,
            GrpcInjectionModule.class,
            MetricsInjectionModule.class,
            AuthorizerInjectionModule.class,
            InfoInjectionModule.class,
            BlockRecordInjectionModule.class,
            PlatformModule.class,
            ThrottleServiceModule.class
        })
public interface HederaInjectionComponent {
    InitTrigger initTrigger();

    /* Needed by ServicesState */
    Provider<QueryInjectionComponent.Factory> queryComponentFactory();

    Provider<IngestInjectionComponent.Factory> ingestComponentFactory();

    WorkingStateAccessor workingStateAccessor();

    RecordCache recordCache();

    GrpcServerManager grpcServerManager();

    Supplier<Charset> nativeCharset();

    NetworkInfo networkInfo();

    PreHandleWorkflow preHandleWorkflow();

    HandleWorkflow handleWorkflow();

    IngestWorkflow ingestWorkflow();

    QueryWorkflow queryWorkflow();

    BlockRecordManager blockRecordManager();

    FeeManager feeManager();

    ExchangeRateManager exchangeRateManager();

    ThrottleServiceManager throttleServiceManager();

    ReconnectCompleteListener reconnectListener();

    StateWriteToDiskCompleteListener stateWriteToDiskListener();

    PlatformStateAccessor platformStateAccessor();

    StoreMetricsService storeMetricsService();

    @Component.Builder
    interface Builder {

        @BindsInstance
        Builder servicesRegistry(ServicesRegistry registry);

        @BindsInstance
        Builder initTrigger(InitTrigger initTrigger);

        @BindsInstance
        Builder crypto(Cryptography engine);

        @BindsInstance
        Builder platform(Platform platform);

        @BindsInstance
        Builder self(final SelfNodeInfo self);

        @BindsInstance
        Builder configProvider(ConfigProvider configProvider);

        @BindsInstance
        Builder configProviderImpl(ConfigProviderImpl configProviderImpl);

        @BindsInstance
        Builder maxSignedTxnSize(@MaxSignedTxnSize final int maxSignedTxnSize);

        @BindsInstance
        Builder currentPlatformStatus(CurrentPlatformStatus currentPlatformStatus);

        @BindsInstance
        Builder instantSource(InstantSource instantSource);

        @BindsInstance
        Builder contractServiceImpl(ContractServiceImpl contractService);

        @BindsInstance
        Builder fileServiceImpl(FileServiceImpl fileService);

        @BindsInstance
        Builder softwareVersion(SemanticVersion softwareVersion);

        @BindsInstance
        Builder metrics(Metrics metrics);

        HederaInjectionComponent build();
    }
}
