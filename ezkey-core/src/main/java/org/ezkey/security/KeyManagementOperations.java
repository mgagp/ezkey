package org.ezkey.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Minimal core-facing contract for resolving the current encryption key state.
 *
 * <p>This keeps re-encryption orchestration decoupled from the concrete Tink key manager
 * implementation so the implementation can move to a dedicated module.
 *
 * @since 2025
 */
public interface KeyManagementOperations {

  /**
   * Returns whether key management is initialized and ready.
   *
   * @return true when key management is initialized and ready
   */
  boolean isInitialized();

  /**
   * Returns the current primary key id from the active keyset.
   *
   * @return the current primary key id from the active keyset
   */
  long getCurrentPrimaryKeyId();

  /**
   * Reloads the active keyset now when the shared store holds a newer version.
   *
   * <p>The routine version check runs only on an encrypt or decrypt call, so an idle node can keep
   * a stale primary after a peer promoted a new key. Re-encryption calls this before it reads the
   * primary (#1431). The default does nothing (no shared store).
   */
  default void refreshKeysetIfStale() {
    // No shared keyset store: nothing to reload.
  }

  /**
   * Rotates the active keyset and promotes the new key immediately.
   *
   * @return the new primary key id
   * @throws GeneralSecurityException when Tink rotation fails
   * @throws IOException when keyset persistence fails
   */
  long rotateKey() throws GeneralSecurityException, IOException;

  /**
   * Adds a new key without promoting it to primary.
   *
   * @return the new key id
   * @throws GeneralSecurityException when key creation fails
   * @throws IOException when keyset persistence fails
   */
  long addKeyWithoutPromotion() throws GeneralSecurityException, IOException;

  /**
   * Promotes an existing key to primary.
   *
   * @param keyId key id to promote
   * @return the previous primary key id
   * @throws GeneralSecurityException when promotion fails
   * @throws IOException when keyset persistence fails
   */
  long promoteToPrimary(long keyId) throws GeneralSecurityException, IOException;

  /**
   * Returns the known key ids from the active keyset.
   *
   * @return key ids currently present in the keyset
   */
  List<Long> getAllKeyIds();
}
