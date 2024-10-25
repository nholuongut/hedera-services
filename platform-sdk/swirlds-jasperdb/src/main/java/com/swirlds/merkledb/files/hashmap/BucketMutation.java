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

package com.swirlds.merkledb.files.hashmap;

import static com.swirlds.merkledb.files.hashmap.HalfDiskHashMap.INVALID_VALUE;

import com.swirlds.virtualmap.VirtualKey;
import java.util.Objects;

/**
 * A simple linked-list of "mutations" for a bucket.
 *
 * @param <K>
 *     The key.
 */
class BucketMutation<K extends VirtualKey> {

    private final K key;
    // If old value is HDHM.INVALID_VALUE, it means the old value should not be checked
    private long oldValue;
    private long value;
    private BucketMutation<K> next;

    BucketMutation(final K key, final long value) {
        this.key = Objects.requireNonNull(key);
        this.value = value;
        this.oldValue = INVALID_VALUE;
        this.next = null;
    }

    BucketMutation(final K key, final long oldValue, final long value) {
        this.key = Objects.requireNonNull(key);
        this.value = value;
        this.oldValue = oldValue;
        this.next = null;
    }

    /**
     * To be called on the "head" of the list, updates an existing mutation
     * with the same key or creates a new mutation at the end of the list.
     * @param key
     * 		The key (cannot be null)
     * @param value
     * 		The value (cannot be null)
     */
    void put(final K key, final long value) {
        BucketMutation<K> mutation = this;
        while (true) {
            if (mutation.key.equals(key)) {
                mutation.value = value;
                mutation.oldValue = INVALID_VALUE;
                return;
            } else if (mutation.next != null) {
                mutation = mutation.next;
            } else {
                mutation.next = new BucketMutation<>(key, value);
                return;
            }
        }
    }

    void putIfEqual(final K key, final long oldValue, final long value) {
        BucketMutation<K> mutation = this;
        while (true) {
            if (mutation.key.equals(key)) {
                if (mutation.value == oldValue) {
                    mutation.value = value;
                }
                return;
            } else if (mutation.next != null) {
                mutation = mutation.next;
            } else {
                mutation.next = new BucketMutation<>(key, oldValue, value);
                return;
            }
        }
    }

    /**
     * Computes the size of the list from this point to the end of the list.
     * @return
     * 		The number of mutations, including this one, from here to the end.
     */
    int size() {
        int size = 1;
        BucketMutation<K> mutation = next;
        while (mutation != null) {
            size++;
            mutation = mutation.next;
        }
        return size;
    }

    // For testing purposes
    long getValue() {
        return value;
    }

    // For testing purposes
    long getOldValue() {
        return oldValue;
    }

    // For testing purposes
    BucketMutation<K> getNext() {
        return next;
    }

    /**
     * Visit each mutation in the list, starting from this mutation.
     * @param consumer
     * 		The callback. Cannot be null.
     */
    void forEachKeyValue(MutationCallback<K> consumer) {
        BucketMutation<K> mutation = this;
        while (mutation != null) {
            consumer.accept(mutation.key, mutation.oldValue, mutation.value);
            mutation = mutation.next;
        }
    }

    /**
     * A simple callback for {@link BucketMutation#forEachKeyValue(MutationCallback)}.
     * @param <K>
     *     The key.
     */
    interface MutationCallback<K extends VirtualKey> {
        void accept(final K key, final long oldValue, final long value);
    }
}
