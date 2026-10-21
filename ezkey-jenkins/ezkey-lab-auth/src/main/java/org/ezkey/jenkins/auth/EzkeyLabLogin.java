package org.ezkey.jenkins.auth;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.security.SecurityRealm;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
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
 * Multi-step Jenkins login against the Integration API (#972 M4).
 *
 * <p>Flow: confirm (GET form) → POST confirm creates attempt → wait page shows challenge → poll
 * wait → ACCEPTED establishes session / REJECTED fails. Never calls Auth API. Never logs challenge
 * or integration secret.
 *
 * @since 2026
 */
@Extension
public class EzkeyLabLogin implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(EzkeyLabLogin.class.getName());

    static final String SESSION_ATTEMPT_ID = "ezkey.lab.pendingAuthAttemptId";
    static final String SESSION_USERNAME = "ezkey.lab.pendingUsername";
    static final String SESSION_CHALLENGE = "ezkey.lab.pendingChallenge";
    static final String SESSION_TIMEOUT = "ezkey.lab.pendingTimeoutSeconds";

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

    /** Confirm form (no create yet). */
    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        req.getView(this, "index.jelly").forward(req, rsp);
    }

    /**
     * Operator confirmed — create Integration auth attempt, then redirect to wait.
     *
     * @param req stapler request
     * @param username Jenkins / Integration userIdentifier
     * @param confirm must be {@code ok} (explicit affirmative)
     * @return redirect or error
     */
    @RequirePOST
    public HttpResponse doConfirm(
            StaplerRequest2 req, @QueryParameter String username, @QueryParameter String confirm) {
        if (!"ok".equalsIgnoreCase(trim(confirm))) {
            return HttpResponses.forwardToView(this, "index.jelly");
        }
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

        clearPending(req.getSession(true));

        try {
            EzkeyClient client = EzkeyLabLoginSupport.newClient(realm);
            EzkeyLabLoginSupport.CreateOutcome created = EzkeyLabLoginSupport.createAfterConfirm(client, user);
            HttpSession session = req.getSession(true);
            session.setAttribute(SESSION_ATTEMPT_ID, created.authAttemptId());
            session.setAttribute(SESSION_USERNAME, user);
            // Challenge held for wait.jelly display only — never written to logs.
            session.setAttribute(SESSION_CHALLENGE, created.challenge());
            session.setAttribute(SESSION_TIMEOUT, created.timeoutSeconds());
            return HttpResponses.redirectTo("wait");
        } catch (EzkeyLabLoginSupport.ChallengeMissingException e) {
            LOGGER.log(
                    Level.WARNING,
                    "ezkey-lab fail-closed: missing challenge authAttemptId={0}",
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

    /** Expose challenge to wait.jelly only (escaped by Jelly). */
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
     * @return JSON-ish plain text status for the wait page
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

        EzkeyMockSecurityRealm realm = requireRealm();
        if (realm == null) {
            return text(500, "NO_REALM");
        }

        try {
            EzkeyClient client = EzkeyLabLoginSupport.newClient(realm);
            AuthAttemptWaitResponse wait = EzkeyLabLoginSupport.waitOnce(client, attemptId, 8);
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
                    || "INVALID".equalsIgnoreCase(status)) {
                clearPending(session);
                return text(200, status.toUpperCase());
            }
            if (wait.timeoutReached() && !wait.completed()) {
                return text(200, "PENDING");
            }
            return text(200, status.isEmpty() ? "PENDING" : status.toUpperCase());
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
            Object idObj = session.getAttribute(SESSION_ATTEMPT_ID);
            if (idObj instanceof Integer attemptId) {
                EzkeyMockSecurityRealm realm = requireRealm();
                if (realm != null) {
                    try {
                        EzkeyLabLoginSupport.bestEffortCancel(EzkeyLabLoginSupport.newClient(realm), attemptId);
                    } catch (IllegalStateException e) {
                        LOGGER.log(Level.FINE, "ezkey-lab cancel skipped: {0}", e.getMessage());
                    }
                }
            }
            clearPending(session);
        }
        return HttpResponses.redirectTo(".");
    }

    @CheckForNull
    private static EzkeyMockSecurityRealm requireRealm() {
        SecurityRealm realm = Jenkins.get().getSecurityRealm();
        return realm instanceof EzkeyMockSecurityRealm ez ? ez : null;
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
