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
    private final Set<Long> directlyTouchedObjects;
    private final Set<Long> touchedUsers;

    SystemTransaction(Xid xid, SystemDictionaryState baseState,
                      Charset characterSet) {
        this.xid = Objects.requireNonNull(xid, "xid");
        this.baseState = Objects.requireNonNull(baseState, "baseState");
        rowPatcher = new SystemDictionaryRowPatcher(characterSet);
        rowValidator = new SystemDictionaryRowValidator();
        replacements = new LinkedHashMap<>();
        deletions = new LinkedHashSet<>();
        directlyTouchedObjects = new LinkedHashSet<>();
        touchedUsers = new LinkedHashSet<>();
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
            replace(key, current, inserted);
            return;
        }
        if (current == null) {
            throw new DataException(50020,
                    "Dictionary row is missing for " + change.operation()
                            + " of " + key);
        }
        if (change.operation() == SystemDictionaryOperation.UPDATE) {
            SystemDictionaryRow updated = rowPatcher.apply(
                    change.table(), change.rowId(), current, change.values());
            replace(key, current, updated);
            return;
        }

        touch(current);
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
        Set<Long> touched = new LinkedHashSet<>(directlyTouchedObjects);
        if (!touchedUsers.isEmpty()) {
            collectObjectsForUsers(touched, baseState);
            collectObjectsForUsers(touched, nextState);
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

    private void replace(SystemDictionaryKey key, SystemDictionaryRow before,
                         SystemDictionaryRow after) {
        touch(before);
        touch(after);
        deletions.remove(key);
        replacements.put(key, after);
    }

    private void touch(SystemDictionaryRow row) {
        if (row == null) {
            return;
        }
        if (row instanceof SysUser user) {
            touchedUsers.add(user.userId());
            return;
        }
        directlyTouchedObjects.add(row.dependentObjectId());
    }

    private void collectObjectsForUsers(Set<Long> touched,
                                        SystemDictionaryState state) {
        for (SysObj object : state.objects()) {
            if (touchedUsers.contains(object.ownerId())) {
                touched.add(object.objectId());
            }
        }
    }
}
