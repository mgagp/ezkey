package org.ezkey.jenkins.auth;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.Secret;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ezkey.sdk.AuthAttemptCreateResponse;
import org.ezkey.sdk.AuthAttemptWaitResponse;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;

/**
 * Thin Integration-API adapter for the Jenkins lab login conversation (#972 M4/M5).
 *
 * <p>Never logs integration secrets or challenge codes. Challenge is returned only for UI display.
 * Challenge request follows realm {@code forceChallenge} (matrix A/B/D in {@code DESIGN.md}).
 *
 * @since 2026
 */
public final class EzkeyLabLoginSupport {

    private static final Logger LOGGER = Logger.getLogger(EzkeyLabLoginSupport.class.getName());

    /** Outcome of send → create (fail-closed when forceChallenge and challenge missing). */
    public record CreateOutcome(
            int authAttemptId,
            @CheckForNull Integer challenge,
            int timeoutSeconds,
            @CheckForNull String expiresAt) {}

    private EzkeyLabLoginSupport() {}

    /**
     * Builds an {@link EzkeyClient} from realm configuration.
     *
     * @param realm configured lab realm
     * @return client aimed at the Integration API
     * @throws IllegalStateException if base URL, integration key, or secret is missing
     */
    public static EzkeyClient newClient(@NonNull EzkeyMockSecurityRealm realm) {
        Objects.requireNonNull(realm, "realm");
        String base = realm.getEzkeyBaseUrl();
        String key = realm.getIntegrationKey();
        String secret = Secret.toString(realm.getEzkeySecret());
        if (base == null || base.isBlank() || key == null || key.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalStateException("Ezkey Integration API base URL, key, and secret must be configured");
        }
        return EzkeyClient.builder()
                .baseUrl(base.trim())
                .integrationKey(key.trim())
                .secretKey(secret)
                .build();
    }

    /**
     * Creates an auth attempt after the operator clicked Send.
     *
     * <p>Passes {@code forceChallenge} as Integration {@code challengeRequested}. When the response
     * includes a challenge, it is returned for display. When forceChallenge is true and no
     * challenge is returned, cancels the attempt (best effort) and fails closed.
     *
     * @param client Integration SDK client
     * @param userIdentifier Jenkins username / Integration userIdentifier
     * @param forceChallenge realm setting — request a challenge and require one when true
     * @return create outcome including optional challenge for display
     * @throws EzkeyException on Integration API errors
     * @throws ChallengeMissingException when forceChallenge and challenge missing
     */
    public static CreateOutcome createAfterConfirm(
            @NonNull EzkeyClient client, @NonNull String userIdentifier, boolean forceChallenge)
            throws EzkeyException, ChallengeMissingException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(userIdentifier, "userIdentifier");
        String user = userIdentifier.trim();
        if (user.isEmpty()) {
            throw new IllegalArgumentException("userIdentifier required");
        }

        AuthAttemptCreateResponse created = client.createAuthAttemptByUserIdentifier(user, forceChallenge);
        LOGGER.log(
                Level.INFO,
                "ezkey-lab Integration create ok authAttemptId={0} user={1} forceChallenge={2}",
                new Object[] {
                    created.authAttemptId(), EzkeyMockSecurityRealm.sanitizeForLog(user), forceChallenge
                });

        try {
            return outcomeFromCreate(created, forceChallenge);
        } catch (ChallengeMissingException e) {
            bestEffortCancel(client, created.authAttemptId());
            throw e;
        }
    }

    /**
     * Challenge display / fail-closed policy after Integration create (matrix A/B/D).
     *
     * <ul>
     *   <li>A — forceChallenge false, no challenge → OK, no code to display
     *   <li>B — challenge non-null → display (regardless of forceChallenge)
     *   <li>D — forceChallenge true, challenge null → fail closed
     * </ul>
     *
     * @param created Integration create response
     * @param forceChallenge realm forceChallenge
     * @return outcome for the wait page
     * @throws ChallengeMissingException when forceChallenge and challenge missing
     */
    public static CreateOutcome outcomeFromCreate(
            @NonNull AuthAttemptCreateResponse created, boolean forceChallenge)
            throws ChallengeMissingException {
        Objects.requireNonNull(created, "created");
        Integer challenge = created.authAttemptChallenge();
        if (challenge == null && forceChallenge) {
            throw new ChallengeMissingException(created.authAttemptId());
        }
        return new CreateOutcome(
                created.authAttemptId(), challenge, created.timeoutSeconds(), created.expiresAt());
    }

    /**
     * Short wait poll for the pending attempt.
     *
     * @param client SDK client
     * @param authAttemptId attempt id
     * @param timeoutSeconds server wait timeout for this poll
     * @return wait response
     * @throws EzkeyException on API errors
     */
    public static AuthAttemptWaitResponse waitOnce(
            @NonNull EzkeyClient client, int authAttemptId, int timeoutSeconds) throws EzkeyException {
        int t = Math.max(1, Math.min(timeoutSeconds, 30));
        return client.waitForAuthAttempt(authAttemptId, t, 2);
    }

    /**
     * Best-effort cancel (abandon / fail-closed). Errors are logged without secrets.
     *
     * @param client SDK client
     * @param authAttemptId attempt id
     */
    public static void bestEffortCancel(@NonNull EzkeyClient client, int authAttemptId) {
        try {
            client.cancelAuthAttempt(authAttemptId);
            LOGGER.log(Level.INFO, "ezkey-lab cancelled authAttemptId={0}", authAttemptId);
        } catch (EzkeyException e) {
            LOGGER.log(
                    Level.WARNING,
                    "ezkey-lab cancel failed authAttemptId={0} status={1}",
                    new Object[] {authAttemptId, e.getStatusCode()});
        }
    }

    /**
     * Thrown when forceChallenge was set but create returned no challenge (fail closed).
     *
     * @since 2026
     */
    public static final class ChallengeMissingException extends Exception {
        private static final long serialVersionUID = 1L;
        private final int authAttemptId;

        /**
         * @param authAttemptId attempt that was cancelled after missing challenge
         */
        public ChallengeMissingException(int authAttemptId) {
            super("Integration API returned no challenge while forceChallenge=true; login denied");
            this.authAttemptId = authAttemptId;
        }

        public int getAuthAttemptId() {
            return authAttemptId;
        }
    }
}
