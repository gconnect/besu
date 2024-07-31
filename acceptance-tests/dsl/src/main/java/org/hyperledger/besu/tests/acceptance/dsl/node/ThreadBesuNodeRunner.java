/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.tests.acceptance.dsl.node;

import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;

import org.hyperledger.besu.Runner;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.BesuControllerBuilder;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.cryptoservices.KeyPairSecurityModule;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.InProcessRpcConfiguration;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters;
import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.MetricsSystemFactory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.PermissioningService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.TransactionPoolValidatorService;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBPlugin;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.BesuEventsImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.BlockchainServiceImpl;
import org.hyperledger.besu.services.PermissioningServiceImpl;
import org.hyperledger.besu.services.PicoCLIOptionsImpl;
import org.hyperledger.besu.services.PrivacyPluginServiceImpl;
import org.hyperledger.besu.services.RpcEndpointServiceImpl;
import org.hyperledger.besu.services.SecurityModuleServiceImpl;
import org.hyperledger.besu.services.StorageServiceImpl;
import org.hyperledger.besu.services.TransactionPoolValidatorServiceImpl;
import org.hyperledger.besu.services.TransactionSelectionServiceImpl;
import org.hyperledger.besu.services.TransactionSimulationServiceImpl;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public class ThreadBesuNodeRunner implements BesuNodeRunner {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadBesuNodeRunner.class);
  private final Map<String, Runner> besuRunners = new HashMap<>();

  private final Map<Node, BesuPluginContextImpl> besuPluginContextMap = new ConcurrentHashMap<>();

  private BesuPluginContextImpl buildPluginContext(
      final BesuNode node,
      final StorageServiceImpl storageService,
      final SecurityModuleServiceImpl securityModuleService,
      final TransactionSimulationServiceImpl transactionSimulationServiceImpl,
      final TransactionSelectionServiceImpl transactionSelectionServiceImpl,
      final TransactionPoolValidatorServiceImpl transactionPoolValidatorServiceImpl,
      final BlockchainServiceImpl blockchainServiceImpl,
      final RpcEndpointServiceImpl rpcEndpointServiceImpl,
      final BesuConfiguration commonPluginConfiguration,
      final PermissioningServiceImpl permissioningService) {
    final CommandLine commandLine = new CommandLine(CommandSpec.create());
    final BesuPluginContextImpl besuPluginContext = new BesuPluginContextImpl();
    besuPluginContext.addService(StorageService.class, storageService);
    besuPluginContext.addService(SecurityModuleService.class, securityModuleService);
    besuPluginContext.addService(PicoCLIOptions.class, new PicoCLIOptionsImpl(commandLine));
    besuPluginContext.addService(RpcEndpointService.class, rpcEndpointServiceImpl);
    besuPluginContext.addService(
        TransactionSelectionService.class, transactionSelectionServiceImpl);
    besuPluginContext.addService(
        TransactionPoolValidatorService.class, transactionPoolValidatorServiceImpl);
    besuPluginContext.addService(
        TransactionSimulationService.class, transactionSimulationServiceImpl);
    besuPluginContext.addService(BlockchainService.class, blockchainServiceImpl);
    besuPluginContext.addService(BesuConfiguration.class, commonPluginConfiguration);

    final Path pluginsPath;
    final String pluginDir = System.getProperty("besu.plugins.dir");
    if (pluginDir == null || pluginDir.isEmpty()) {
      pluginsPath = node.homeDirectory().resolve("plugins");
      final File pluginsDirFile = pluginsPath.toFile();
      if (!pluginsDirFile.isDirectory()) {
        pluginsDirFile.mkdirs();
        pluginsDirFile.deleteOnExit();
      }
      System.setProperty("besu.plugins.dir", pluginsPath.toString());
    } else {
      pluginsPath = Path.of(pluginDir);
    }

    besuPluginContext.addService(BesuConfiguration.class, commonPluginConfiguration);
    besuPluginContext.addService(PermissioningService.class, permissioningService);
    besuPluginContext.addService(PrivacyPluginService.class, new PrivacyPluginServiceImpl());

    besuPluginContext.registerPlugins(new PluginConfiguration(pluginsPath));

    commandLine.parseArgs(node.getConfiguration().getExtraCLIOptions().toArray(new String[0]));

    // register built-in plugins
    new RocksDBPlugin().register(besuPluginContext);
    return besuPluginContext;
  }

  @Override
  public void startNode(final BesuNode node) {

    if (MDC.get("node") != null) {
      LOG.error("ThreadContext node is already set to {}", MDC.get("node"));
    }
    MDC.put("node", node.getName());

    if (!node.getRunCommand().isEmpty()) {
      throw new UnsupportedOperationException("commands are not supported with thread runner");
    }

    final StorageServiceImpl storageService = new StorageServiceImpl();
    final SecurityModuleServiceImpl securityModuleService = new SecurityModuleServiceImpl();
    final TransactionSimulationServiceImpl transactionSimulationServiceImpl =
        new TransactionSimulationServiceImpl();
    final TransactionSelectionServiceImpl transactionSelectionServiceImpl =
        new TransactionSelectionServiceImpl();
    final TransactionPoolValidatorServiceImpl transactionPoolValidatorServiceImpl =
        new TransactionPoolValidatorServiceImpl();
    final BlockchainServiceImpl blockchainServiceImpl = new BlockchainServiceImpl();
    final RpcEndpointServiceImpl rpcEndpointServiceImpl = new RpcEndpointServiceImpl();
    final Path dataDir = node.homeDirectory();
    final BesuConfigurationImpl commonPluginConfiguration = new BesuConfigurationImpl();
    final PermissioningServiceImpl permissioningService = new PermissioningServiceImpl();

    final var miningParameters =
        ImmutableMiningParameters.builder()
            .from(node.getMiningParameters())
            .transactionSelectionService(transactionSelectionServiceImpl)
            .build();
    commonPluginConfiguration
        .init(dataDir, dataDir.resolve(DATABASE_PATH), node.getDataStorageConfiguration())
        .withMiningParameters(miningParameters);

    final BesuPluginContextImpl besuPluginContext =
        besuPluginContextMap.computeIfAbsent(
            node,
            n ->
                buildPluginContext(
                    node,
                    storageService,
                    securityModuleService,
                    transactionSimulationServiceImpl,
                    transactionSelectionServiceImpl,
                    transactionPoolValidatorServiceImpl,
                    blockchainServiceImpl,
                    rpcEndpointServiceImpl,
                    commonPluginConfiguration,
                    permissioningService));

    GlobalOpenTelemetry.resetForTest();
    final ObservableMetricsSystem metricsSystem =
        MetricsSystemFactory.create(node.getMetricsConfiguration());
    final List<EnodeURL> bootnodes =
        node.getConfiguration().getBootnodes().stream()
            .map(EnodeURLImpl::fromURI)
            .collect(Collectors.toList());
    final NetworkName network = node.getNetwork() == null ? NetworkName.DEV : node.getNetwork();
    final EthNetworkConfig.Builder networkConfigBuilder =
        new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(network))
            .setBootNodes(bootnodes);
    node.getConfiguration()
        .getGenesisConfig()
        .map(GenesisConfigFile::fromConfig)
        .ifPresent(networkConfigBuilder::setGenesisConfigFile);
    final EthNetworkConfig ethNetworkConfig = networkConfigBuilder.build();
    final SynchronizerConfiguration synchronizerConfiguration =
        new SynchronizerConfiguration.Builder().build();
    final BesuControllerBuilder builder =
        new BesuController.Builder()
            .fromEthNetworkConfig(ethNetworkConfig, synchronizerConfiguration.getSyncMode());

    final KeyValueStorageProvider storageProvider =
        new KeyValueStorageProviderBuilder()
            .withStorageFactory(storageService.getByName("rocksdb").get())
            .withCommonConfiguration(commonPluginConfiguration)
            .withMetricsSystem(metricsSystem)
            .build();

    final TransactionPoolConfiguration txPoolConfig =
        ImmutableTransactionPoolConfiguration.builder()
            .from(node.getTransactionPoolConfiguration())
            .strictTransactionReplayProtectionEnabled(node.isStrictTxReplayProtectionEnabled())
            .transactionPoolValidatorService(transactionPoolValidatorServiceImpl)
            .build();

    final InProcessRpcConfiguration inProcessRpcConfiguration = node.inProcessRpcConfiguration();

    final int maxPeers = 25;

    builder
        .synchronizerConfiguration(new SynchronizerConfiguration.Builder().build())
        .dataDirectory(node.homeDirectory())
        .miningParameters(miningParameters)
        .privacyParameters(node.getPrivacyParameters())
        .nodeKey(new NodeKey(new KeyPairSecurityModule(KeyPairUtil.loadKeyPair(dataDir))))
        .metricsSystem(metricsSystem)
        .transactionPoolConfiguration(txPoolConfig)
        .dataStorageConfiguration(DataStorageConfiguration.DEFAULT_FOREST_CONFIG)
        .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
        .clock(Clock.systemUTC())
        .isRevertReasonEnabled(node.isRevertReasonEnabled())
        .storageProvider(storageProvider)
        .gasLimitCalculator(GasLimitCalculator.constant())
        .evmConfiguration(EvmConfiguration.DEFAULT)
        .maxPeers(maxPeers)
        .maxRemotelyInitiatedPeers(15)
        .networkConfiguration(node.getNetworkingConfiguration())
        .randomPeerPriority(false);

    node.getGenesisConfig()
        .map(GenesisConfigFile::fromConfig)
        .ifPresent(builder::genesisConfigFile);

    final BesuController besuController = builder.build();

    initTransactionSimulationService(
        transactionSimulationServiceImpl, besuController, node.getApiConfiguration());
    initBlockchainService(blockchainServiceImpl, besuController);

    final RunnerBuilder runnerBuilder = new RunnerBuilder();
    runnerBuilder.permissioningConfiguration(node.getPermissioningConfiguration());
    runnerBuilder.apiConfiguration(node.getApiConfiguration());

    runnerBuilder
        .vertx(Vertx.vertx())
        .besuController(besuController)
        .ethNetworkConfig(ethNetworkConfig)
        .discovery(node.isDiscoveryEnabled())
        .p2pAdvertisedHost(node.getHostName())
        .p2pListenPort(0)
        .networkingConfiguration(node.getNetworkingConfiguration())
        .jsonRpcConfiguration(node.jsonRpcConfiguration())
        .webSocketConfiguration(node.webSocketConfiguration())
        .jsonRpcIpcConfiguration(node.jsonRpcIpcConfiguration())
        .dataDir(node.homeDirectory())
        .metricsSystem(metricsSystem)
        .permissioningService(permissioningService)
        .metricsConfiguration(node.getMetricsConfiguration())
        .p2pEnabled(node.isP2pEnabled())
        .p2pTLSConfiguration(node.getTLSConfiguration())
        .graphQLConfiguration(GraphQLConfiguration.createDefault())
        .staticNodes(
            node.getStaticNodes().stream()
                .map(EnodeURLImpl::fromString)
                .collect(Collectors.toList()))
        .besuPluginContext(besuPluginContext)
        .autoLogBloomCaching(false)
        .storageProvider(storageProvider)
        .rpcEndpointService(rpcEndpointServiceImpl)
        .inProcessRpcConfiguration(inProcessRpcConfiguration);
    node.engineRpcConfiguration().ifPresent(runnerBuilder::engineJsonRpcConfiguration);

    besuPluginContext.beforeExternalServices();
    final Runner runner = runnerBuilder.build();

    runner.startExternalServices();

    besuPluginContext.addService(
        BesuEvents.class,
        new BesuEventsImpl(
            besuController.getProtocolContext().getBlockchain(),
            besuController.getProtocolManager().getBlockBroadcaster(),
            besuController.getTransactionPool(),
            besuController.getSyncState(),
            besuController.getProtocolContext().getBadBlockManager()));

    rpcEndpointServiceImpl.init(runner.getInProcessRpcMethods());

    besuPluginContext.startPlugins();

    runner.startEthereumMainLoop();

    besuRunners.put(node.getName(), runner);
    MDC.remove("node");
  }

  private void initBlockchainService(
      final BlockchainServiceImpl blockchainServiceImpl, final BesuController besuController) {
    blockchainServiceImpl.init(
        besuController.getProtocolContext(), besuController.getProtocolSchedule());
  }

  private void initTransactionSimulationService(
      final TransactionSimulationServiceImpl transactionSimulationService,
      final BesuController besuController,
      final ApiConfiguration apiConfiguration) {
    transactionSimulationService.init(
        besuController.getProtocolContext().getBlockchain(),
        new TransactionSimulator(
            besuController.getProtocolContext().getBlockchain(),
            besuController.getProtocolContext().getWorldStateArchive(),
            besuController.getProtocolSchedule(),
            apiConfiguration.getGasCap()));
  }

  @Override
  public void stopNode(final BesuNode node) {
    final BesuPluginContextImpl pluginContext = besuPluginContextMap.remove(node);
    if (pluginContext != null) {
      pluginContext.stopPlugins();
    }
    node.stop();
    killRunner(node.getName());
  }

  @Override
  public void shutdown() {
    // stop all plugins from pluginContext
    besuPluginContextMap.values().forEach(BesuPluginContextImpl::stopPlugins);
    besuPluginContextMap.clear();

    // iterate over a copy of the set so that besuRunner can be updated when a runner is killed
    new HashSet<>(besuRunners.keySet()).forEach(this::killRunner);
  }

  @Override
  public boolean isActive(final String nodeName) {
    return besuRunners.containsKey(nodeName);
  }

  private void killRunner(final String name) {
    LOG.info("Killing " + name + " runner");

    if (besuRunners.containsKey(name)) {
      try {
        besuRunners.get(name).close();
        besuRunners.remove(name);
      } catch (final Exception e) {
        throw new RuntimeException("Error shutting down node " + name, e);
      }
    } else {
      LOG.error("There was a request to kill an unknown node: {}", name);
    }
  }

  @Override
  public void startConsoleCapture() {
    throw new RuntimeException("Console contents can only be captured in process execution");
  }

  @Override
  public String getConsoleContents() {
    throw new RuntimeException("Console contents can only be captured in process execution");
  }
}
