package org.ezkey.jenkins.auth;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.util.Secret;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Passwordless Jenkins SecurityRealm for the ezkey-jenkins lab (#1184).
 *
 * <p>Ezkey is the only factor for enabled users. Break-glass remains a local password on the
 * standard Jenkins login form so an operator is never locked out when the Integration API is down.
 * The integration secret is a {@link Secret} and must never be logged. Credentials-plugin migration
 * stays deferred — see {@code ezkey-jenkins/DESIGN.md}.
 *
 * @since 2026
 */
public class EzkeySecurityRealm extends AbstractPasswordBasedSecurityRealm {

    private static final Logger LOGGER = Logger.getLogger(EzkeySecurityRealm.class.getName());

    /** Minimum gap between Integration pushes for the same Jenkins username. */
    public static final long SEND_COOLDOWN_MILLIS = 30_000L;

    private final String ezkeyBaseUrl;
    private final String integrationKey;
    private final Secret ezkeySecret;
    private final String breakGlassUsername;
    private final Secret breakGlassPassword;

    /**
     * When true (the default), every send requests a challenge and login fails closed if none
     * returns. Number matching is the passwordless posture; turning this off is an explicit
     * weakening.
     */
    private boolean forceChallenge = true;

    private List<EzkeyAccount> enabledUsers = new ArrayList<>();

    private final Map<String, Long> lastSendMillis = new ConcurrentHashMap<>();

    private transient LongSupplier clock = System::currentTimeMillis;

    /**
     * @param ezkeyBaseUrl Integration API base URL (e.g. {@code http://integration-api:7080})
     * @param integrationKey Ezkey integration key
     * @param ezkeySecret Ezkey integration secret (Jenkins {@link Secret}; never logged)
     * @param breakGlassUsername local admin username that bypasses Ezkey
     * @param breakGlassPassword local password for the break-glass user
     */
    @DataBoundConstructor
    public EzkeySecurityRealm(
            String ezkeyBaseUrl,
            String integrationKey,
            Secret ezkeySecret,
            String breakGlassUsername,
            Secret breakGlassPassword) {
        this.ezkeyBaseUrl = ezkeyBaseUrl == null ? "" : ezkeyBaseUrl.trim();
        this.integrationKey = integrationKey == null ? "" : integrationKey.trim();
        this.ezkeySecret = ezkeySecret;
        this.breakGlassUsername =
                breakGlassUsername == null || breakGlassUsername.isBlank()
                        ? "admin"
                        : breakGlassUsername.trim();
        this.breakGlassPassword = breakGlassPassword;
    }

    /**
     * Sign in goes to the Ezkey page. Break-glass stays on core {@code /login}, which posts to
     * {@code j_spring_security_check} and does not follow this URL.
     */
    @Override
    public String getLoginUrl() {
        return "ezkey-login";
    }

    public String getEzkeyBaseUrl() {
        return ezkeyBaseUrl;
    }

    public String getIntegrationKey() {
        return integrationKey;
    }

    public Secret getEzkeySecret() {
        return ezkeySecret;
    }

    public String getBreakGlassUsername() {
        return breakGlassUsername;
    }

    public Secret getBreakGlassPassword() {
        return breakGlassPassword;
    }

    /**
     * Whether this realm forces a challenge code on every Integration create.
     *
     * @return true when number matching is required (the default)
     */
    public boolean isForceChallenge() {
        return forceChallenge;
    }

    /**
     * Opt out of mandatory number matching. Default is {@code true}.
     *
     * @param forceChallenge false only when an admin explicitly accepts approve/deny without a code
     */
    @DataBoundSetter
    public void setForceChallenge(boolean forceChallenge) {
        this.forceChallenge = forceChallenge;
    }

    /**
     * Jenkins users allowed to start an Ezkey sign-in. Everyone else gets the same wait page and
     * no push.
     *
     * @return configured accounts (never null)
     */
    @NonNull
    public List<EzkeyAccount> getEnabledUsers() {
        return enabledUsers == null ? List.of() : List.copyOf(enabledUsers);
    }

    /**
     * Replaces the enabled-user list.
     *
     * @param enabledUsers accounts that may receive a push; null clears the list
     */
    @DataBoundSetter
    public void setEnabledUsers(@CheckForNull List<EzkeyAccount> enabledUsers) {
        this.enabledUsers = enabledUsers == null ? new ArrayList<>() : new ArrayList<>(enabledUsers);
    }

    /**
     * @param username Jenkins username typed on the sign-in form
     * @return the enabled account, or null when this username must not receive a push
     */
    @CheckForNull
    public EzkeyAccount findEnabledAccount(@CheckForNull String username) {
        if (username == null || username.isBlank() || enabledUsers == null) {
            return null;
        }
        for (EzkeyAccount account : enabledUsers) {
            if (account != null && username.equals(account.getUsername())) {
                return account;
            }
        }
        return null;
    }

    /**
     * Integration {@code userIdentifier} for an enabled Jenkins user. Blank mapping means the
     * Jenkins username.
     *
     * @param username Jenkins username
     * @return identifier sent to the Integration API
     */
    @NonNull
    public String integrationUserIdentifier(@NonNull String username) {
        EzkeyAccount account = findEnabledAccount(username);
        if (account == null || account.getUserIdentifier() == null || account.getUserIdentifier().isBlank()) {
            return username;
        }
        return account.getUserIdentifier();
    }

    /**
     * @param username Jenkins username
     * @return false when a push was recorded for this user inside {@link #SEND_COOLDOWN_MILLIS}
     */
    public boolean allowSend(@NonNull String username) {
        Long last = lastSendMillis.get(username);
        return last == null || now() - last >= SEND_COOLDOWN_MILLIS;
    }

    /**
     * Starts the cooldown window after a successful Integration create.
     *
     * @param username Jenkins username
     */
    public void recordSend(@NonNull String username) {
        lastSendMillis.put(username, now());
    }

    /** Clock used for cooldown and wait deadlines. Tests replace it. */
    long now() {
        LongSupplier current = clock == null ? System::currentTimeMillis : clock;
        return current.getAsLong();
    }

    /**
     * Replaces the clock. Not a configuration field.
     *
     * @param clock millisecond source
     */
    void setClockForTest(@NonNull LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    protected UserDetails authenticate2(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new BadCredentialsException("username required");
        }
        if (password == null || password.isEmpty()) {
            throw new BadCredentialsException("password required");
        }

        if (breakGlassUsername.equals(username)) {
            String expected = Secret.toString(breakGlassPassword);
            if (expected == null || expected.isEmpty() || !expected.equals(password)) {
                LOGGER.warning("ezkey-login break-glass rejected for user=" + sanitizeForLog(username));
                throw new BadCredentialsException("break-glass credentials rejected");
            }
            LOGGER.warning("ezkey-login break-glass login ok user=" + sanitizeForLog(username));
            return userDetails(username);
        }

        LOGGER.info(
                "ezkey-login password login denied for non-break-glass user="
                        + sanitizeForLog(username)
                        + " — use /ezkey-login/");
        throw new BadCredentialsException("Use /ezkey-login/ for Ezkey (not break-glass password login)");
    }

    @Override
    public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("empty");
        }
        if (breakGlassUsername.equals(username) || findEnabledAccount(username) != null) {
            return userDetails(username);
        }
        throw new UsernameNotFoundException(username);
    }

    private static UserDetails userDetails(String username) {
        return new User(
                username, "", true, true, true, true, Collections.singleton(AUTHENTICATED_AUTHORITY2));
    }

    @Override
    public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) {
        return new GroupDetails() {
            @Override
            public String getName() {
                return groupname;
            }
        };
    }

    static String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(c == '\r' || c == '\n' ? '_' : c);
        }
        return sb.toString();
    }

    /**
     * One Jenkins user allowed to receive an Ezkey push.
     *
     * @since 2026
     */
    public static final class EzkeyAccount implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String username;
        private final String userIdentifier;

        /**
         * @param username Jenkins username typed at sign-in
         * @param userIdentifier Integration userIdentifier; blank uses {@code username}
         */
        @DataBoundConstructor
        public EzkeyAccount(String username, String userIdentifier) {
            this.username = username == null ? "" : username.trim();
            this.userIdentifier = userIdentifier == null ? "" : userIdentifier.trim();
        }

        public String getUsername() {
            return username;
        }

        public String getUserIdentifier() {
            return userIdentifier;
        }
    }

    /** Descriptor shown under Manage Jenkins → Security. JCasC key is {@code ezkey}. */
    @Extension
    @Symbol("ezkey")
    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.EzkeySecurityRealm_DescriptorImpl_DisplayName();
        }
    }
}
