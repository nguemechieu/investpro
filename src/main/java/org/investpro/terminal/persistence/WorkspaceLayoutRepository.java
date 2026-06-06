package org.investpro.terminal.persistence;

import java.util.Optional;

public interface WorkspaceLayoutRepository {
    void saveLayout(String workspaceId, String layoutJson);
    Optional<String> findLayout(String workspaceId);
}
