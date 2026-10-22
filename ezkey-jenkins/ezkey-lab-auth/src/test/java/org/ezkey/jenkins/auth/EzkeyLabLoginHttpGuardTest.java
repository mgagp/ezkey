package org.ezkey.jenkins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import hudson.security.csrf.DefaultCrumbIssuer;
import hudson.util.Secret;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.sdk.EzkeyClient;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.Cookie;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Mutation-killing HTTP / Integration guards (#972 Patrick B2 + Christophe M2).
 *
 * <ul>
 *   <li>GET send → 405 — kills removal of {@code @RequirePOST}
 *   <li>POST without crumb → 403 — CrumbFilter still enforced
 *   <li>index form embeds crumb field
 *   <li>case D create → {@code bestEffortCancel} actually called (live HTTP stub)
 *   <li>ACCEPTED poll rotates session id (session fixation)
 * </ul>
 *
 * @since 2026
 */
@WithJenkins
class EzkeyLabLoginHttpGuardTest {

    @Test
    void sendRejectsGetWith405(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(labRealm("http://127.0.0.1:9"));
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        WebRequest req =
                new WebRequest(new URL(j.getURL(), "ezkey-lab-login/send"), HttpMethod.GET);
        Page page = wc.getPage(req);
        assertEquals(405, page.getWebResponse().getStatusCode());
    }

    @Test
    void sendRejectsPostWithoutCrumbWith403(JenkinsRule j) throws Exception {
        j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
        j.jenkins.setSecurityRealm(labRealm("http://127.0.0.1:9"));
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        // Intentionally omit wc.addCrumb(req) — CSRF must reject.
        WebRequest req =
                new WebRequest(new URL(j.getURL(), "ezkey-lab-login/send"), HttpMethod.POST);
        req.setRequestParameters(List.of(new NameValuePair("username", "labuser")));
        Page page = wc.getPage(req);
        assertEquals(403, page.getWebResponse().getStatusCode());
    }

    @Test
    void indexFormIncludesCrumbField(JenkinsRule j) throws Exception {
        j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
        j.jenkins.setSecurityRealm(labRealm("http://127.0.0.1:9"));
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("ezkey-lab-login/");
        HtmlForm form = page.getForms().get(0);
        assertEquals("send", form.getActionAttribute());
        String crumbField = j.jenkins.getCrumbIssuer().getCrumbRequestField();
        assertTrue(
                form.getInputsByName(crumbField).size() >= 1,
                "index.jelly must embed CSRF crumb (kills mutation that strips jelly crumb)");
    }

    @Test
    void acceptedPollRotatesSessionId(JenkinsRule j) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v1/auth-attempts",
                exchange -> {
                    String method = exchange.getRequestMethod();
                    String path = exchange.getRequestURI().getPath();
                    exchange.getRequestBody().readAllBytes();
                    if ("POST".equals(method) && path.equals("/api/v1/auth-attempts")) {
                        byte[] body =
                                """
                                {"authAttemptId":42,"authAttemptChallenge":null,"timeoutSeconds":60,"expiresAt":"t"}
                                """
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(201, body.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(body);
                        }
                        return;
                    }
                    if ("GET".equals(method) && path.contains("/wait")) {
                        byte[] body =
                                """
                                {"status":"ACCEPTED","completed":true,"timeoutReached":false}
                                """
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(body);
                        }
                        return;
                    }
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
            j.jenkins.setSecurityRealm(labRealm(base));

            JenkinsRule.WebClient wc = j.createWebClient();
            wc.setThrowExceptionOnFailingStatusCode(false);
            // Disable wait.jelly auto-poll JS so we control the ACCEPTED poll ourselves.
            wc.getOptions().setJavaScriptEnabled(false);
            HtmlPage index = wc.goTo("ezkey-lab-login/");
            HtmlForm form = index.getForms().get(0);
            form.getInputByName("username").setValueAttribute("labuser");
            // JenkinsRule.submit injects a valid crumb (form POST path).
            HtmlPage waitPage = j.submit(form);
            assertTrue(
                    waitPage.getUrl().getPath().contains("wait")
                            || waitPage.asNormalizedText().contains("Awaiting"),
                    "send should reach wait; url="
                            + waitPage.getUrl()
                            + " text="
                            + waitPage.asNormalizedText().substring(
                                    0, Math.min(200, waitPage.asNormalizedText().length())));

            String before = sessionCookieValue(wc);
            assertNotNull(before, "pre-auth session cookie after send");

            Page poll =
                    wc.getPage(
                            new WebRequest(new URL(j.getURL(), "ezkey-lab-login/poll"), HttpMethod.GET));
            assertEquals(
                    200,
                    poll.getWebResponse().getStatusCode(),
                    "poll should succeed: " + poll.getWebResponse().getContentAsString());
            assertEquals("ACCEPTED", poll.getWebResponse().getContentAsString().trim());

            String after = sessionCookieValue(wc);
            assertNotNull(after, "post-auth session cookie after ACCEPTED");
            assertNotEquals(before, after, "establishLogin must rotate session id (session fixation)");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void createAfterConfirmCancelsWhenChallengeMissingUnderForce() throws Exception {
        AtomicInteger cancelHits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v1/auth-attempts",
                exchange -> {
                    String method = exchange.getRequestMethod();
                    String path = exchange.getRequestURI().getPath();
                    exchange.getRequestBody().readAllBytes();
                    if ("POST".equals(method) && path.equals("/api/v1/auth-attempts")) {
                        // forceChallenge create with missing challenge (case D)
                        byte[] body =
                                """
                                {"authAttemptId":77,"authAttemptChallenge":null,"timeoutSeconds":60,"expiresAt":"t"}
                                """
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(201, body.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(body);
                        }
                        return;
                    }
                    if ("POST".equals(method) && path.endsWith("/cancel")) {
                        cancelHits.incrementAndGet();
                        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(body);
                        }
                        return;
                    }
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            EzkeyClient client =
                    EzkeyClient.builder()
                            .baseUrl(base)
                            .integrationKey("ikey")
                            .secretKey("skey")
                            .build();
            assertThrows(
                    EzkeyLabLoginSupport.ChallengeMissingException.class,
                    () -> EzkeyLabLoginSupport.createAfterConfirm(client, "labuser", true));
            assertEquals(1, cancelHits.get(), "case D must best-effort cancel the dangling attempt");
        } finally {
            server.stop(0);
        }
    }

    private static String sessionCookieValue(JenkinsRule.WebClient wc) {
        for (Cookie cookie : wc.getCookieManager().getCookies()) {
            if ("JSESSIONID".equalsIgnoreCase(cookie.getName())
                    || cookie.getName().startsWith("JSESSIONID")) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static EzkeyMockSecurityRealm labRealm(String baseUrl) {
        return new EzkeyMockSecurityRealm(
                baseUrl,
                "ikey",
                Secret.fromString("skey"),
                "admin",
                Secret.fromString("ezkey-lab-admin"));
    }
}
