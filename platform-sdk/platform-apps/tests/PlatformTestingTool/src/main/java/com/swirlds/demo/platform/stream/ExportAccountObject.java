/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform.stream;

public class ExportAccountObject {
    public ExportAccountObject(long shardNum, long realmNum, long accountNum, long balance) {
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.accountNum = accountNum;
        this.balance = balance;
    }

    private long shardNum;
    private long realmNum;
    private long accountNum;
    private long balance;

    public long getShardNum() {
        return shardNum;
    }

    public void setShardNum(long shardNum) {
        this.shardNum = shardNum;
    }

    public long getRealmNum() {
        return realmNum;
    }

    public void setRealmNum(long realmNum) {
        this.realmNum = realmNum;
    }

    public long getAccountNum() {
        return accountNum;
    }

    public void setAccountNum(long accountNum) {
        this.accountNum = accountNum;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }
}
