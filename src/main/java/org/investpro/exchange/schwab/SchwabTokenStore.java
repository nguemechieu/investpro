package org.investpro.exchange.schwab;

import java.io.IOException;
import java.util.Optional;

public interface SchwabTokenStore {
    Optional<SchwabTokenState> load() throws IOException;
    void save(SchwabTokenState state) throws IOException;
    void clear() throws IOException;
}
