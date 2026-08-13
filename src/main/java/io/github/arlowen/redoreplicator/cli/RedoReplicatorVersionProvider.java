/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.cli;

import picocli.CommandLine.IVersionProvider;

public final class RedoReplicatorVersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
        String version = RedoReplicatorVersionProvider.class
                .getPackage().getImplementationVersion();
        if (version == null) {
            version = "development";
        }
        return new String[]{"RedoReplicator " + version};
    }
}
