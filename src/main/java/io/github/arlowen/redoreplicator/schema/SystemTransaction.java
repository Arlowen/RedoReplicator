/*
 * Java translation derived from OpenLogReplicator
 * src/builder/SystemTransaction.h and SystemTransaction.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.Xid;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class SystemTransaction {
    private final Xid xid;
    private final SystemDictionaryState baseState;
    private final SystemDictionaryRowPatcher rowPatcher;
    private final SystemDictionaryRowValidator rowValidator;
    private final Map<SystemDictionaryKey, SystemDictionaryRow> replacements;
    private final Set<SystemDictionaryKey> deletions;

    SystemTransaction(Xid xid, SystemDictionaryState baseState,
                      Charset characterSet) {
        this.xid = Objects.requireNonNull(xid, "xid");
        this.baseState = Objects.requireNonNull(baseState, "baseState");
        rowPatcher = new SystemDictionaryRowPatcher(characterSet);
        rowValidator = new SystemDictionaryRowValidator();
        replacements = new LinkedHashMap<>();
        deletions = new LinkedHashSet<>();
    }

    void apply(SystemDictionaryChange change) {
        SystemDictionaryKey key = new SystemDictionaryKey(
                change.table(), change.rowId());
        SystemDictionaryRow current = visibleRow(key);
        if (change.operation() == SystemDictionaryOperation.INSERT) {
            if (current != null) {
                throw new DataException(50020,
                        "Duplicate dictionary insert for " + key);
            }
            SystemDictionaryRow inserted = rowPatcher.apply(
                    change.table(), change.rowId(), null, change.values());
            rowValidator.validateInsert(inserted, change.values());
            replace(key, inserted);
            return;
        }
        if (current == null) {
            return;
        }
        if (change.operation() == SystemDictionaryOperation.UPDATE) {
            SystemDictionaryRow updated = rowPatcher.apply(
                    change.table(), change.rowId(), current, change.values());
            replace(key, updated);
            return;
        }

        replacements.remove(key);
        deletions.add(key);
    }

    SystemDictionaryState commitAgainst(SystemDictionaryState latestState) {
        for (SystemDictionaryKey key : changedKeys()) {
            SystemDictionaryRow baseRow = baseState.find(key);
            SystemDictionaryRow latestRow = latestState.find(key);
            if (!Objects.equals(baseRow, latestRow)) {
                throw new DataException(50071,
                        "Conflicting committed dictionary change for " + key
                                + " while committing " + xid);
            }
        }
        return latestState.withChanges(replacements, deletions);
    }

    Set<Long> touchedObjectIds(SystemDictionaryState nextState) {
        Set<Long> touched = new LinkedHashSet<>();
        for (SystemDictionaryKey key : changedKeys()) {
            SystemDictionaryRow before = baseState.find(key);
            SystemDictionaryRow after = nextState.find(key);
            collectDependentObjects(before, baseState, touched);
            collectDependentObjects(before, nextState, touched);
            collectDependentObjects(after, baseState, touched);
            collectDependentObjects(after, nextState, touched);
        }
        touched.remove(0L);
        return Set.copyOf(touched);
    }

    private SystemDictionaryRow visibleRow(SystemDictionaryKey key) {
        if (deletions.contains(key)) {
            return null;
        }
        SystemDictionaryRow replacement = replacements.get(key);
        if (replacement != null) {
            return replacement;
        }
        return baseState.find(key);
    }

    private Set<SystemDictionaryKey> changedKeys() {
        Set<SystemDictionaryKey> keys = new LinkedHashSet<>(replacements.keySet());
        keys.addAll(deletions);
        return keys;
    }

    private void replace(SystemDictionaryKey key, SystemDictionaryRow after) {
        deletions.remove(key);
        replacements.put(key, after);
    }

    private static void collectDependentObjects(
            SystemDictionaryRow row, SystemDictionaryState state,
            Set<Long> touched) {
        if (row == null) {
            return;
        }
        if (row instanceof SysUser user) {
            for (SysObj object : state.objects()) {
                if (object.ownerId() == user.userId()) {
                    touched.add(object.objectId());
                }
            }
            return;
        }
        touched.add(row.dependentObjectId());
        if (row instanceof SysLobCompPart partition) {
            collectTableForLob(partition.lobObjectId(), state, touched);
            return;
        }
        if (row instanceof SysLobFrag fragment) {
            collectTableForLobParent(fragment.parentObjectId(), state, touched);
            return;
        }
        if (row instanceof SysTabSubPart subpartition) {
            for (SysTabComPart partition : state.tableCompositePartitions()) {
                if (partition.objectId() == subpartition.parentObjectId()) {
                    touched.add(partition.baseObjectId());
                }
            }
            return;
        }
        if (row instanceof SysTs tablespace) {
            for (SysLob lob : state.lobs()) {
                if (lob.tablespaceId() == tablespace.tablespaceId()) {
                    touched.add(lob.objectId());
                }
            }
            for (SysLobFrag fragment : state.lobFragments()) {
                if (fragment.tablespaceId() == tablespace.tablespaceId()) {
                    collectTableForLobParent(fragment.parentObjectId(), state, touched);
                }
            }
            return;
        }
        if (row instanceof SysObj object) {
            collectTableForLob(object.objectId(), state, touched);
            for (SysLobFrag fragment : state.lobFragments()) {
                if (fragment.fragmentObjectId() == object.objectId()) {
                    collectTableForLobParent(fragment.parentObjectId(), state, touched);
                }
            }
            collectTableForLobIndex(object, state, touched);
        }
    }

    private static void collectTableForLob(
            long lobObjectId, SystemDictionaryState state, Set<Long> touched) {
        for (SysLob lob : state.lobs()) {
            if (lob.lobObjectId() == lobObjectId) {
                touched.add(lob.objectId());
            }
        }
    }

    private static void collectTableForLobParent(
            long parentObjectId, SystemDictionaryState state, Set<Long> touched) {
        collectTableForLob(parentObjectId, state, touched);
        for (SysLobCompPart partition : state.lobCompositePartitions()) {
            if (partition.partitionObjectId() == parentObjectId) {
                collectTableForLob(partition.lobObjectId(), state, touched);
            }
        }
    }

    private static void collectTableForLobIndex(
            SysObj object, SystemDictionaryState state, Set<Long> touched) {
        for (SysLob lob : state.lobs()) {
            String indexName = String.format(
                    "SYS_IL%010dC%05d$$", lob.objectId(), lob.internalColumn());
            if (object.name().equals(indexName)) {
                touched.add(lob.objectId());
            }
        }
    }
}
