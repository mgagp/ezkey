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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * Mutation-killing HTTP / Integration guards (#972 Patrick + Christophe).
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
        HttpServer server = pendingThenAcceptedServer(42);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
            j.jenkins.setSecurityRealm(labRealm(base));

            JenkinsRule.WebClient wc = j.createWebClient();
            wc.setThrowExceptionOnFailingStatusCode(false);
            // Disable wait.jelly auto-poll JS so we control the ACCEPTED poll ourselves.
            wc.getOptions().setJavaScriptEnabled(false);
            submitSend(j, wc, "labuser");

            String before = sessionCookieValue(wc);
            assertNotNull(before, "pre-auth session cookie after send");

            Page poll =
                    wc.getPage(
                            new WebRequest(new URL(j.getURL(), "ezkey-lab-login/poll"), HttpMethod.GET));
            assertEquals(200, poll.getWebResponse().getStatusCode());
            assertEquals("ACCEPTED", poll.getWebResponse().getContentAsString().trim());

            String after = sessionCookieValue(wc);
            assertNotNull(after, "post-auth session cookie after ACCEPTED");
            assertNotEquals(before, after, "establishLogin must rotate session id (session fixation)");
        } finally {
            server.stop(0);
        }
    }

    /**
     * B1: past SESSION_TIMEOUT deadline → 410 EXPIRED + cancel (kills removal of isPastDeadline /
     * cancel-on-expiry).
     */
    @Test
    void expiredDeadlinePollReturns410AndCancels(JenkinsRule j) throws Exception {
        AtomicInteger cancelHits = new AtomicInteger();
        List<Integer> cancelledIds = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v1/auth-attempts",
                exchange -> {
                    String method = exchange.getRequestMethod();
                    String path = exchange.getRequestURI().getPath();
                    exchange.getRequestBody().readAllBytes();
                    if ("POST".equals(method) && path.equals("/api/v1/auth-attempts")) {
                        // timeoutSeconds=1 drives SESSION_DEADLINE_MS / SESSION_TIMEOUT.
                        byte[] body =
                                """
                                {"authAttemptId":55,"authAttemptChallenge":null,"timeoutSeconds":1,"expiresAt":"t"}
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
                        cancelledIds.add(Integer.parseInt(path.replaceAll(".*/(\\d+)/cancel", "$1")));
                        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(body);
                        }
                        return;
                    }
                    if ("GET".equals(method) && path.contains("/wait")) {
                        // If deadline check is removed, wait would return PENDING (mutation survives).
                        byte[] body =
                                """
                                {"status":"PENDING","completed":false,"timeoutReached":true}
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
            wc.getOptions().setJavaScriptEnabled(false);
            submitSend(j, wc, "labuser");

            Thread.sleep(1100);

            Page poll =
                    wc.getPage(
                            new WebRequest(new URL(j.getURL(), "ezkey-lab-login/poll"), HttpMethod.GET));
            assertEquals(
                    410,
                    poll.getWebResponse().getStatusCode(),
                    "past SESSION_TIMEOUT deadline must be terminal non-2xx");
            assertEquals("EXPIRED", poll.getWebResponse().getContentAsString().trim());
            assertEquals(1, cancelHits.get(), "deadline expiry must cancel the attempt");
            assertEquals(List.of(55), new ArrayList<>(cancelledIds));
        } finally {
            server.stop(0);
        }
    }

    /**
     * B1: wait.jelly with JS enabled stops on 502 and shows the error result (kills infinite
     * retry on Integration failure).
     */
    @Test
    void waitPageJsStopsOn502AndShowsError(JenkinsRule j) throws Exception {
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
                                {"authAttemptId":88,"authAttemptChallenge":null,"timeoutSeconds":60,"expiresAt":"t"}
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
                        // Integration failure → doPoll returns 502 ERROR → jelly !res.ok → error.
                        exchange.sendResponseHeaders(500, -1);
                        exchange.close();
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
            wc.getOptions().setJavaScriptEnabled(true);
            wc.getOptions().setTimeout(15_000);
            submitSend(j, wc, "labuser");

            wc.waitForBackgroundJavaScript(8_000);
            HtmlPage page = (HtmlPage) wc.getCurrentWindow().getEnclosedPage();
            assertTrue(
                    page.getUrl().getPath().contains("result"),
                    "wait JS must navigate to result on 502; url=" + page.getUrl());
            assertTrue(
                    page.asXml().contains("data-ezkey-lab-state=\"error\"")
                            || page.asNormalizedText().toLowerCase().contains("error"),
                    "result page must show error state");
        } finally {
            server.stop(0);
        }
    }

    /** M1: second Send cancels the first Integration attempt id. */
    @Test
    void secondSendCancelsPreviousAttempt(JenkinsRule j) throws Exception {
        AtomicInteger nextId = new AtomicInteger(200);
        List<Integer> created = new CopyOnWriteArrayList<>();
        List<Integer> cancelled = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v1/auth-attempts",
                exchange -> {
                    String method = exchange.getRequestMethod();
                    String path = exchange.getRequestURI().getPath();
                    exchange.getRequestBody().readAllBytes();
                    if ("POST".equals(method) && path.equals("/api/v1/auth-attempts")) {
                        int id = nextId.getAndIncrement();
                        created.add(id);
                        String json =
                                "{\"authAttemptId\":"
                                        + id
                                        + ",\"authAttemptChallenge\":null,\"timeoutSeconds\":60,\"expiresAt\":\"t\"}";
                        byte[] body = json.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(201, body.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(body);
                        }
                        return;
                    }
                    if ("POST".equals(method) && path.endsWith("/cancel")) {
                        cancelled.add(Integer.parseInt(path.replaceAll(".*/(\\d+)/cancel", "$1")));
                        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(body);
                        }
                        return;
                    }
                    if ("GET".equals(method) && path.contains("/wait")) {
                        byte[] body =
                                """
                                {"status":"PENDING","completed":false,"timeoutReached":true}
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
            wc.getOptions().setJavaScriptEnabled(false);

            submitSend(j, wc, "labuser");
            assertEquals(1, created.size());
            int firstId = created.get(0);

            // Second send from wait page: go back to index and send again (same session).
            wc.goTo("ezkey-lab-login/");
            submitSend(j, wc, "labuser");
            assertEquals(2, created.size());
            assertTrue(
                    cancelled.contains(firstId),
                    "second Send must cancel first attemptId="
                            + firstId
                            + "; cancelled="
                            + cancelled);
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

    private static void submitSend(JenkinsRule j, JenkinsRule.WebClient wc, String username)
            throws Exception {
        HtmlPage index = wc.goTo("ezkey-lab-login/");
        HtmlForm form = index.getForms().get(0);
        form.getInputByName("username").setValueAttribute(username);
        HtmlPage waitPage = j.submit(form);
        assertTrue(
                waitPage.getUrl().getPath().contains("wait")
                        || waitPage.asNormalizedText().contains("Awaiting"),
                "send should reach wait; url=" + waitPage.getUrl());
    }

    private static HttpServer pendingThenAcceptedServer(int attemptId) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v1/auth-attempts",
                exchange -> {
                    String method = exchange.getRequestMethod();
                    String path = exchange.getRequestURI().getPath();
                    exchange.getRequestBody().readAllBytes();
                    if ("POST".equals(method) && path.equals("/api/v1/auth-attempts")) {
                        String json =
                                "{\"authAttemptId\":"
                                        + attemptId
                                        + ",\"authAttemptChallenge\":null,\"timeoutSeconds\":60,\"expiresAt\":\"t\"}";
                        byte[] body = json.getBytes(StandardCharsets.UTF_8);
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
        return server;
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
