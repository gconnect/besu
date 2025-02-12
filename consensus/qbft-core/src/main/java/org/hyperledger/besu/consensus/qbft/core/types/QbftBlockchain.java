/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.core.types;

/** Minimal QBFT interface for reading data from the blockchain. */
public interface QbftBlockchain {
  /**
   * Returns the chain head header.
   *
   * @return the header.
   */
  QbftBlockHeader getChainHeadHeader();

  /**
   * Returns the chain head block number.
   *
   * @return the block number.
   */
  long getChainHeadBlockNumber();
}
