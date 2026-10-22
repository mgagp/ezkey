package org.ezkey.jenkins.auth;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.security.SecurityRealm;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.SecurityListener;
import org.ezkey.sdk.AuthAttemptWaitResponse;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.GET;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Multi-step Jenkins login against the Integration API (#972 M4/M5).
 *
 * <p>Flow: form (GET) → POST send creates attempt → wait page shows challenge when present → poll
 * wait → result states (approved / denied / expired / cancelled / error). Never calls Auth API.
 * Never logs challenge or integration secret.
 *
 * @since 2026
 */
@Extension
public class EzkeyLabLogin implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(EzkeyLabLogin.class.getName());

    static final String SESSION_ATTEMPT_ID = "ezkey.lab.pendingAuthAttemptId";
    static final String SESSION_USERNAME = "ezkey.lab.pendingUsername";
    static final String SESSION_CHALLENGE = "ezkey.lab.pendingChallenge";
    /** Integration create {@code timeoutSeconds} — drives wait-page deadline. */
    static final String SESSION_TIMEOUT = "ezkey.lab.pendingTimeoutSeconds";
    /** Epoch millis when the wait loop must stop (now + timeout at create). */
    static final String SESSION_DEADLINE_MS = "ezkey.lab.pendingDeadlineMs";

    static final String ATTR_RESULT_STATE = "ezkeyLabResultState";

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @NonNull
    @Override
    public String getUrlName() {
        return "ezkey-lab-login";
    }

    /** Send form (no create yet). CSRF crumb required on POST. */
    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        req.getView(this, "index.jelly").forward(req, rsp);
    }

    /**
     * Operator clicked Send — create Integration auth attempt, then redirect to wait.
     *
     * @param req stapler request
     * @param username Jenkins / Integration userIdentifier
     * @return redirect or error
     */
    @RequirePOST
    public HttpResponse doSend(StaplerRequest2 req, @QueryParameter String username) {
        String user = trim(username);
        if (user.isEmpty()) {
            return withError(req, "Username required.");
        }

        EzkeyMockSecurityRealm realm = requireRealm();
        if (realm == null) {
            return withError(req, "Ezkey Lab Auth realm is not the active SecurityRealm.");
        }
        if (realm.getBreakGlassUsername().equals(user)) {
            return withError(req, "Break-glass admin uses the standard Jenkins login form, not Ezkey MFA.");
        }

        HttpSession session = req.getSession(true);
        // Double-submit / resend: cancel the previous Integration attempt before replacing it.
        bestEffortCancelPending(session, realm);
        clearPending(session);
        boolean forceChallenge = realm.isForceChallenge();

        try {
            EzkeyClient client = EzkeyLabLoginSupport.newClient(realm);
            EzkeyLabLoginSupport.CreateOutcome created =
                    EzkeyLabLoginSupport.createAfterConfirm(client, user, forceChallenge);
            session.setAttribute(SESSION_ATTEMPT_ID, created.authAttemptId());
            session.setAttribute(SESSION_USERNAME, user);
            // Challenge held for wait.jelly display only when present — never written to logs.
            if (created.challenge() != null) {
                session.setAttribute(SESSION_CHALLENGE, created.challenge());
            }
            int timeoutSeconds = Math.max(1, created.timeoutSeconds());
            session.setAttribute(SESSION_TIMEOUT, timeoutSeconds);
            session.setAttribute(
                    SESSION_DEADLINE_MS, System.currentTimeMillis() + timeoutSeconds * 1000L);
            return HttpResponses.redirectTo("wait");
        } catch (EzkeyLabLoginSupport.ChallengeMissingException e) {
            LOGGER.log(
                    Level.WARNING,
                    "ezkey-lab fail-closed: missing challenge authAttemptId={0} forceChallenge=true",
                    e.getAuthAttemptId());
            return withError(req, "Ezkey did not return a challenge code. Login denied.");
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "ezkey-lab not configured: {0}", e.getMessage());
            return withError(req, "Ezkey Integration API is not configured on the SecurityRealm.");
        } catch (EzkeyException e) {
            LOGGER.log(
                    Level.WARNING,
                    "ezkey-lab create failed user={0} httpStatus={1}",
                    new Object[] {EzkeyMockSecurityRealm.sanitizeForLog(user), e.getStatusCode()});
            return withError(
                    req, "Could not start Ezkey authentication. Check Integration key and enrollment.");
        }
    }

    /**
     * Compatibility alias for older lab scripts that POST to {@code confirm}.
     *
     * @param req stapler request
     * @param username Jenkins / Integration userIdentifier
     * @return redirect or error
     */
    @RequirePOST
    public HttpResponse doConfirm(StaplerRequest2 req, @QueryParameter String username) {
        return doSend(req, username);
    }

    /** Wait page: shows challenge; client polls {@code poll}. */
    @GET
    public void doWait(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute(SESSION_ATTEMPT_ID) == null) {
            rsp.sendRedirect(".");
            return;
        }
        req.getView(this, "wait.jelly").forward(req, rsp);
    }

    /**
     * Formats the pending challenge for wait.jelly.
     *
     * <p>Integration challenge codes are two-digit values in {@code 00..99}; {@code %02d} zero-pads
     * within that range.
     *
     * @return zero-padded two-digit code, or null when absent
     */
    @CheckForNull
    public String getPendingChallengeFormatted() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) {
            return null;
        }
        HttpSession session = req.getSession(false);
        if (session == null) {
            return null;
        }
        Object c = session.getAttribute(SESSION_CHALLENGE);
        if (c instanceof Integer i) {
            // Challenge codes are 00–99 (Integration API); %02d assumes that range.
            return String.format("%02d", i);
        }
        return null;
    }

    @CheckForNull
    public String getPendingUsername() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) {
            return null;
        }
        HttpSession session = req.getSession(false);
        if (session == null) {
            return null;
        }
        Object u = session.getAttribute(SESSION_USERNAME);
        return u instanceof String s ? s : null;
    }

    public int getPendingAuthAttemptId() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) {
            return -1;
        }
        HttpSession session = req.getSession(false);
        if (session == null) {
            return -1;
        }
        Object id = session.getAttribute(SESSION_ATTEMPT_ID);
        return id instanceof Integer i ? i : -1;
    }

    /**
     * Integration create timeout (seconds) for the wait-page deadline.
     *
     * @return timeout seconds, or {@link EzkeyLabLoginSupport#CLIENT_POLL_WAIT_SECONDS} fallback
     */
    public int getPendingTimeoutSeconds() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) {
            return EzkeyLabLoginSupport.CLIENT_POLL_WAIT_SECONDS;
        }
        HttpSession session = req.getSession(false);
        if (session == null) {
            return EzkeyLabLoginSupport.CLIENT_POLL_WAIT_SECONDS;
        }
        Object t = session.getAttribute(SESSION_TIMEOUT);
        if (t instanceof Integer i && i > 0) {
            return i;
        }
        return EzkeyLabLoginSupport.CLIENT_POLL_WAIT_SECONDS;
    }

    /** Wait-page poll interval (ms) — single source with unit tests. */
    public int getWaitPagePollIntervalMs() {
        return EzkeyLabLoginSupport.WAIT_PAGE_POLL_INTERVAL_MS;
    }

    /** Wait-page poll retry after transport error (ms). */
    public int getWaitPagePollErrorIntervalMs() {
        return EzkeyLabLoginSupport.WAIT_PAGE_POLL_ERROR_INTERVAL_MS;
    }

    @CheckForNull
    public String getErrorMessage() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) {
            return null;
        }
        Object m = req.getAttribute("ezkeyLabError");
        return m instanceof String s ? s : null;
    }

    /**
     * Poll Integration wait once; on ACCEPTED establish Jenkins session.
     *
     * @param req stapler request
     * @return plain-text status for the wait page
     */
    @GET
    public HttpResponse doPoll(StaplerRequest2 req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return text(401, "NO_SESSION");
        }
        Object idObj = session.getAttribute(SESSION_ATTEMPT_ID);
        Object userObj = session.getAttribute(SESSION_USERNAME);
        if (!(idObj instanceof Integer attemptId) || !(userObj instanceof String username)) {
            return text(401, "NO_PENDING");
        }

        if (isPastDeadline(session)) {
            clearPending(session);
            return text(200, "EXPIRED");
        }

        EzkeyMockSecurityRealm realm = requireRealm();
        if (realm == null) {
            return text(500, "NO_REALM");
        }

        try {
            EzkeyClient client = EzkeyLabLoginSupport.newClient(realm);
            AuthAttemptWaitResponse wait =
                    EzkeyLabLoginSupport.waitOnce(
                            client, attemptId, EzkeyLabLoginSupport.CLIENT_POLL_WAIT_SECONDS);
            String status = wait.status() == null ? "" : wait.status();
            LOGGER.log(
                    Level.INFO,
                    "ezkey-lab wait poll authAttemptId={0} status={1} completed={2}",
                    new Object[] {attemptId, status, wait.completed()});

            if ("ACCEPTED".equalsIgnoreCase(status)) {
                establishLogin(req, realm, username);
                clearPending(session);
                return text(200, "ACCEPTED");
            }
            if ("REJECTED".equalsIgnoreCase(status)
                    || "EXPIRED".equalsIgnoreCase(status)
                    || "CANCELLED".equalsIgnoreCase(status)
                    || "INVALID".equalsIgnoreCase(status)) {
                clearPending(session);
                return text(200, status.toUpperCase(Locale.ROOT));
            }
            if (wait.timeoutReached() && !wait.completed()) {
                return text(200, "PENDING");
            }
            return text(200, status.isEmpty() ? "PENDING" : status.toUpperCase(Locale.ROOT));
        } catch (EzkeyException e) {
            if (e.isReadTimeout()) {
                return text(200, "PENDING");
            }
            LOGGER.log(
                    Level.WARNING,
                    "ezkey-lab wait failed authAttemptId={0} httpStatus={1}",
                    new Object[] {attemptId, e.getStatusCode()});
            return text(502, "ERROR");
        }
    }

    /** Operator abandoned the wait — cancel Integration attempt. */
    @RequirePOST
    public HttpResponse doCancel(StaplerRequest2 req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            bestEffortCancelPending(session, requireRealm());
            clearPending(session);
        }
        return HttpResponses.redirectTo("result?state=cancelled");
    }

    /**
     * Terminal result page (approved / denied / expired / cancelled / error).
     *
     * @param req stapler request
     * @param rsp stapler response
     * @param state result state query param
     * @throws IOException on redirect/write failure
     * @throws ServletException on view forward failure
     */
    @GET
    public void doResult(
            StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String state)
            throws IOException, ServletException {
        String normalized = normalizeResultState(state);
        req.setAttribute(ATTR_RESULT_STATE, normalized);
        req.getView(this, "result.jelly").forward(req, rsp);
    }

    /** Machine state key for {@code result.jelly} ({@code data-ezkey-lab-state}). */
    @NonNull
    public String getResultState() {
        return currentResultState();
    }

    /** Short human label for the result state line. */
    @NonNull
    public String getResultStateLabel() {
        return switch (currentResultState()) {
            case "approved" -> "Approved";
            case "denied" -> "Denied";
            case "expired" -> "Expired";
            case "cancelled" -> "Cancelled";
            case "error" -> "Error";
            default -> "Result";
        };
    }

    /** Result page heading. */
    @NonNull
    public String getResultTitle() {
        return switch (currentResultState()) {
            case "approved" -> "Approved";
            case "denied" -> "Denied";
            case "expired" -> "Expired";
            case "cancelled" -> "Cancelled";
            case "error" -> "Sign-in error";
            default -> "Ezkey login";
        };
    }

    /** Operator-facing result explanation. */
    @NonNull
    public String getResultMessage() {
        return switch (currentResultState()) {
            case "approved" -> "Device approved. Your Jenkins session is ready.";
            case "denied" -> "Device denied this sign-in. No Jenkins session was created.";
            case "expired" -> "The auth attempt expired before a device response. Try again.";
            case "cancelled" -> "Sign-in was cancelled. No Jenkins session was created.";
            case "error" -> "Ezkey login could not continue (session lost or Integration error). Try again.";
            default -> "Unknown result.";
        };
    }

    @NonNull
    private String currentResultState() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) {
            return "unknown";
        }
        Object s = req.getAttribute(ATTR_RESULT_STATE);
        return s instanceof String str ? str : "unknown";
    }

    @NonNull
    private static String normalizeResultState(@CheckForNull String state) {
        if (state == null || state.isBlank()) {
            return "unknown";
        }
        return switch (state.trim().toLowerCase(Locale.ROOT)) {
            case "approved", "accepted" -> "approved";
            case "denied", "rejected" -> "denied";
            case "expired" -> "expired";
            case "cancelled", "canceled", "invalid" -> "cancelled";
            case "error" -> "error";
            default -> "unknown";
        };
    }

    @CheckForNull
    private static EzkeyMockSecurityRealm requireRealm() {
        SecurityRealm realm = Jenkins.get().getSecurityRealm();
        return realm instanceof EzkeyMockSecurityRealm ez ? ez : null;
    }

    private static boolean isPastDeadline(HttpSession session) {
        Object deadline = session.getAttribute(SESSION_DEADLINE_MS);
        return deadline instanceof Long d && System.currentTimeMillis() > d;
    }

    private static void bestEffortCancelPending(
            HttpSession session, @CheckForNull EzkeyMockSecurityRealm realm) {
        Object idObj = session.getAttribute(SESSION_ATTEMPT_ID);
        if (!(idObj instanceof Integer attemptId) || realm == null) {
            return;
        }
        try {
            EzkeyLabLoginSupport.bestEffortCancel(EzkeyLabLoginSupport.newClient(realm), attemptId);
        } catch (IllegalStateException e) {
            LOGGER.log(Level.FINE, "ezkey-lab cancel skipped: {0}", e.getMessage());
        }
    }

    private static void establishLogin(StaplerRequest2 req, EzkeyMockSecurityRealm realm, String username) {
        UserDetails details = realm.loadUserByUsername2(username);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        HttpSession session = req.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        SecurityListener.fireLoggedIn(username);
        LOGGER.log(
                Level.INFO,
                "ezkey-lab Jenkins session established user={0}",
                EzkeyMockSecurityRealm.sanitizeForLog(username));
    }

    private static void clearPending(HttpSession session) {
        session.removeAttribute(SESSION_ATTEMPT_ID);
        session.removeAttribute(SESSION_USERNAME);
        session.removeAttribute(SESSION_CHALLENGE);
        session.removeAttribute(SESSION_TIMEOUT);
        session.removeAttribute(SESSION_DEADLINE_MS);
    }

    private HttpResponse withError(StaplerRequest2 req, String message) {
        req.setAttribute("ezkeyLabError", message);
        return HttpResponses.forwardToView(this, "error.jelly");
    }

    private static HttpResponse text(int status, String body) {
        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node)
                    throws IOException, ServletException {
                rsp.setStatus(status);
                rsp.setContentType("text/plain;charset=UTF-8");
                rsp.getWriter().write(body);
            }
        };
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
