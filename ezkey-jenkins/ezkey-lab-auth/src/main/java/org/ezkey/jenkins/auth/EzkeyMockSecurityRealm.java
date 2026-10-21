package org.ezkey.jenkins.auth;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.util.Secret;
import java.util.Collections;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Lab SecurityRealm for #972: break-glass local admin + Ezkey Integration MFA path.
 *
 * <p>Break-glass authenticates with a local password (anti-lockout) via the standard Jenkins login
 * form. All other users use {@link EzkeyLabLogin} (confirm → create → challenge → wait) against the
 * Integration API via {@code org.ezkey:ezkey-sdk}. This realm never calls the Auth API.
 *
 * <p>The Ezkey integration secret is stored as {@link Secret}. Secret and challenge codes must never
 * appear in logs. Credentials-plugin migration remains deferred — see {@code
 * ezkey-jenkins/DESIGN.md}.
 *
 * @since 2026
 */
public class EzkeyMockSecurityRealm extends AbstractPasswordBasedSecurityRealm {

    private static final Logger LOGGER = Logger.getLogger(EzkeyMockSecurityRealm.class.getName());

    private final String ezkeyBaseUrl;
    private final String integrationKey;
    private final Secret ezkeySecret;
    private final String breakGlassUsername;
    private final Secret breakGlassPassword;

    /**
     * @param ezkeyBaseUrl Integration API base URL (e.g. {@code http://host.docker.internal:7080})
     * @param integrationKey Ezkey integration key
     * @param ezkeySecret Ezkey integration secret (Jenkins {@link Secret}; never logged)
     * @param breakGlassUsername local admin username that bypasses Ezkey MFA
     * @param breakGlassPassword local password for the break-glass user
     */
    @DataBoundConstructor
    public EzkeyMockSecurityRealm(
            String ezkeyBaseUrl,
            String integrationKey,
            Secret ezkeySecret,
            String breakGlassUsername,
            Secret breakGlassPassword) {
        this.ezkeyBaseUrl = ezkeyBaseUrl == null ? "" : ezkeyBaseUrl.trim();
        this.integrationKey = integrationKey == null ? "" : integrationKey.trim();
        this.ezkeySecret = ezkeySecret;
        this.breakGlassUsername =
                breakGlassUsername == null || breakGlassUsername.isBlank() ? "admin" : breakGlassUsername.trim();
        this.breakGlassPassword = breakGlassPassword;
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
                LOGGER.warning("ezkey-lab break-glass login rejected for user=" + sanitizeForLog(username));
                throw new BadCredentialsException("break-glass credentials rejected");
            }
            LOGGER.info("ezkey-lab break-glass login ok user=" + sanitizeForLog(username));
            return userDetails(username);
        }

        // Non-break-glass users must complete EzkeyLabLogin (Integration MFA). Basic-auth / form
        // password path intentionally does not auto-accept (M4).
        LOGGER.info(
                "ezkey-lab password login denied for non-break-glass user="
                        + sanitizeForLog(username)
                        + " — use /ezkey-lab-login/");
        throw new BadCredentialsException("Use /ezkey-lab-login/ for Ezkey MFA (not break-glass password login)");
    }

    @Override
    public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("empty");
        }
        return userDetails(username);
    }

    private static UserDetails userDetails(String username) {
        return new User(username, "", true, true, true, true, Collections.singleton(AUTHENTICATED_AUTHORITY2));
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

    @Extension
    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Ezkey Lab Auth (experimental)";
        }
    }
}
