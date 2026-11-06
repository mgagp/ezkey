package org.ezkey.jenkins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import hudson.security.csrf.DefaultCrumbIssuer;
import hudson.util.Secret;
import java.nio.file.Files;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.net.URL;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Phase 2 passwordless policy (#1184): only enabled users get a push, number matching is the
 * default, and a second push inside the cooldown is refused.
 *
 * @since 2026
 */
@WithJenkins
class EzkeyPasswordlessHttpTest {

    private static final String SECRET = "skey-do-not-log";

    @Test
    void enabledUserGetsChallengeAndUnknownUserGetsNoPush(JenkinsRule j) throws Exception {
        AtomicInteger creates = new AtomicInteger();
        HttpServer server = createServer(creates, 42, 17);
        server.start();
        try {
            EzkeySecurityRealm realm = realm(base(server));
            j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
            j.jenkins.setSecurityRealm(realm);
            JenkinsRule.WebClient enabled = client(j);
            HtmlPage enabledWait = submit(j, enabled, "labuser", false);
            assertTrue(enabledWait.getUrl().getPath().contains("wait"), enabledWait.getUrl().toString());
            assertTrue(enabledWait.asXml().contains("data-testid=\"ezkey-lab-challenge\""));
            assertTrue(enabledWait.asXml().contains(">17<") || enabledWait.asNormalizedText().contains("17"));
            assertEquals(1, creates.get());

            JenkinsRule.WebClient unknown = client(j);
            HtmlPage unknownWait = submit(j, unknown, "nobody", false);
            assertTrue(unknownWait.getUrl().getPath().contains("wait"), unknownWait.getUrl().toString());
            assertTrue(unknownWait.asXml().contains("data-ezkey-lab-state=\"waiting\""));
            assertTrue(unknownWait.asXml().contains("data-testid=\"ezkey-lab-challenge\""));
            assertEquals(1, creates.get(), "unknown username must not create an Integration attempt");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cooldownBlocksASecondPush(JenkinsRule j) throws Exception {
        AtomicInteger creates = new AtomicInteger();
        HttpServer server = createServer(creates, 7, 21);
        server.start();
        try {
            j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
            j.jenkins.setSecurityRealm(realm(base(server)));
            JenkinsRule.WebClient first = client(j);
            submit(j, first, "labuser", false);
            JenkinsRule.WebClient second = client(j);
            HtmlPage decoy = submit(j, second, "labuser", false);
            assertTrue(decoy.asXml().contains("data-ezkey-lab-state=\"waiting\""));
            assertEquals(1, creates.get(), "second browser inside the cooldown must not push again");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sameSessionResendInsideCooldownKeepsTheFirstAttempt(JenkinsRule j) throws Exception {
        AtomicInteger creates = new AtomicInteger();
        List<Integer> cancelled = new CopyOnWriteArrayList<>();
        HttpServer server = createServer(creates, 9, 11, cancelled);
        server.start();
        try {
            j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
            j.jenkins.setSecurityRealm(realm(base(server)));
            JenkinsRule.WebClient wc = client(j);
            submit(j, wc, "labuser", false);
            wc.goTo("ezkey-login/");
            submit(j, wc, "labuser", false);
            assertEquals(1, creates.get());
            assertTrue(cancelled.isEmpty(), "cooldown must not cancel the attempt the user is still waiting on");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingChallengeFailsClosedAndCancels(JenkinsRule j) throws Exception {
        AtomicInteger creates = new AtomicInteger();
        List<Integer> cancelled = new CopyOnWriteArrayList<>();
        HttpServer server = createServer(creates, 77, null, cancelled);
        server.start();
        try {
            j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
            j.jenkins.setSecurityRealm(realm(base(server)));
            JenkinsRule.WebClient wc = client(j);
            HtmlPage page = submit(j, wc, "labuser", false);
            assertTrue(
                    page.asNormalizedText().contains("did not return a challenge"),
                    page.asNormalizedText());
            assertEquals(List.of(77), List.copyOf(cancelled));
            Page who = wc.getPage(new WebRequest(new URL(j.getURL(), "whoAmI/api/json")));
            assertTrue(who.getWebResponse().getContentAsString().contains("anonymous"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void requireChallengeCheckboxCanOnlyHarden(JenkinsRule j) throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer server = bodyCapturingServer(bodies, 15);
        server.start();
        try {
            EzkeySecurityRealm realm = realm(base(server));
            realm.setForceChallenge(false);
            realm.setEnabledUsers(List.of(new EzkeySecurityRealm.EzkeyAccount("labuser", "lab-id")));
            j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
            j.jenkins.setSecurityRealm(realm);
            JenkinsRule.WebClient wc = client(j);
            submit(j, wc, "labuser", true);
            assertEquals(1, bodies.size());
            assertTrue(bodies.get(0).contains("\"userIdentifier\":\"lab-id\""), bodies.get(0));
            assertTrue(bodies.get(0).contains("\"challengeRequested\":true"), bodies.get(0));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectedPollDoesNotCreateASession(JenkinsRule j) throws Exception {
        HttpServer server = waitStatusServer("REJECTED");
        server.start();
        try {
            j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
            j.jenkins.setSecurityRealm(realm(base(server)));
            JenkinsRule.WebClient wc = client(j);
            submit(j, wc, "labuser", false);
            String poll =
                    wc.getPage(new WebRequest(new URL(j.getURL(), "ezkey-login/poll")))
                            .getWebResponse()
                            .getContentAsString()
                            .trim();
            assertEquals("REJECTED", poll);
            String who =
                    wc.getPage(new WebRequest(new URL(j.getURL(), "whoAmI/api/json")))
                            .getWebResponse()
                            .getContentAsString();
            assertTrue(who.contains("anonymous"), who);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancelledPollDoesNotCreateASession(JenkinsRule j) throws Exception {
        HttpServer server = waitStatusServer("CANCELLED");
        server.start();
        try {
            j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
            j.jenkins.setSecurityRealm(realm(base(server)));
            JenkinsRule.WebClient wc = client(j);
            submit(j, wc, "labuser", false);
            String poll =
                    wc.getPage(new WebRequest(new URL(j.getURL(), "ezkey-login/poll")))
                            .getWebResponse()
                            .getContentAsString()
                            .trim();
            assertEquals("CANCELLED", poll);
            String who =
                    wc.getPage(new WebRequest(new URL(j.getURL(), "whoAmI/api/json")))
                            .getWebResponse()
                            .getContentAsString();
            assertTrue(who.contains("anonymous"), who);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void breakGlassWorksWhenIntegrationApiIsDown(JenkinsRule j) throws Exception {
        EzkeySecurityRealm realm = realm("http://127.0.0.1:9");
        j.jenkins.setSecurityRealm(realm);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("admin", "ezkey-lab-admin");
        String who =
                wc.getPage(new WebRequest(new URL(j.getURL(), "whoAmI/api/json")))
                        .getWebResponse()
                        .getContentAsString();
        assertTrue(who.contains("\"admin\""), who);
    }

    @Test
    void secretIsNotLogged(JenkinsRule j) throws Exception {
        Logger realmLog = Logger.getLogger(EzkeySecurityRealm.class.getName());
        Logger loginLog = Logger.getLogger(EzkeyLabLogin.class.getName());
        Logger supportLog = Logger.getLogger(EzkeyLabLoginSupport.class.getName());
        List<String> lines = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                StringBuilder sb = new StringBuilder();
                sb.append(record.getMessage());
                Object[] params = record.getParameters();
                if (params != null) {
                    for (Object param : params) {
                        sb.append(' ').append(param);
                    }
                }
                lines.add(sb.toString());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        handler.setLevel(Level.ALL);
        for (Logger logger : List.of(realmLog, loginLog, supportLog)) {
            logger.setLevel(Level.ALL);
            logger.addHandler(handler);
        }
        try {
            AtomicInteger creates = new AtomicInteger();
            HttpServer server = createServer(creates, 3, 8);
            server.start();
            try {
                j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
                j.jenkins.setSecurityRealm(realm(base(server)));
                submit(j, client(j), "labuser", false);
                ((EzkeySecurityRealm) j.jenkins.getSecurityRealm())
                        .authenticateByPassword("admin", "ezkey-lab-admin");
            } finally {
                server.stop(0);
            }
            String joined = String.join("\n", lines);
            assertFalse(joined.contains(SECRET), joined);
        } finally {
            for (Logger logger : List.of(realmLog, loginLog, supportLog)) {
                logger.removeHandler(handler);
            }
        }
    }

    @Test
    void configRoundTripKeepsUsersAndSecret(JenkinsRule j) throws Exception {
        EzkeySecurityRealm realm = realm("http://127.0.0.1:9");
        realm.setEnabledUsers(List.of(new EzkeySecurityRealm.EzkeyAccount("labuser", "lab-id")));
        j.jenkins.setSecurityRealm(realm);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("admin", "ezkey-lab-admin");
        HtmlForm form = wc.goTo("configureSecurity").getFormByName("config");
        j.submit(form);
        EzkeySecurityRealm after = (EzkeySecurityRealm) j.jenkins.getSecurityRealm();
        assertEquals("lab-id", after.integrationUserIdentifier("labuser"));
        assertEquals(SECRET, Secret.toString(after.getEzkeySecret()));
        assertTrue(after.isForceChallenge());
        assertEquals("ezkey-login", after.getLoginUrl());
    }

    @Test
    void jcascRoundTrip(JenkinsRule j) throws Exception {
        String yaml =
                """
                jenkins:
                  securityRealm:
                    ezkey:
                      ezkeyBaseUrl: "http://127.0.0.1:9"
                      integrationKey: "ikey"
                      ezkeySecret: "%s"
                      breakGlassUsername: "admin"
                      breakGlassPassword: "ezkey-lab-admin"
                      forceChallenge: true
                      enabledUsers:
                        - username: "labuser"
                          userIdentifier: "lab-id"
                """
                        .formatted(SECRET);
        java.nio.file.Path yamlFile = Files.createTempFile("ezkey-jcasc", ".yaml");
        Files.writeString(yamlFile, yaml);
        try {
            ConfigurationAsCode.get().configure(yamlFile.toString());
        } finally {
            Files.deleteIfExists(yamlFile);
        }
        EzkeySecurityRealm realm = (EzkeySecurityRealm) j.jenkins.getSecurityRealm();
        assertEquals("lab-id", realm.integrationUserIdentifier("labuser"));
        assertEquals(SECRET, Secret.toString(realm.getEzkeySecret()));
        assertTrue(realm.isForceChallenge());
        assertEquals("admin", realm.authenticateByPassword("admin", "ezkey-lab-admin").getUsername());
    }

    private static JenkinsRule.WebClient client(JenkinsRule j) {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        wc.getOptions().setJavaScriptEnabled(false);
        return wc;
    }

    private static HtmlPage submit(JenkinsRule j, JenkinsRule.WebClient wc, String username, boolean requireChallenge)
            throws Exception {
        HtmlPage index = wc.goTo("ezkey-login/");
        HtmlForm form = index.getForms().get(0);
        form.getInputByName("username").setValueAttribute(username);
        if (requireChallenge) {
            form.getInputByName("requireChallenge").setChecked(true);
        }
        return j.submit(form);
    }

    private static String base(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static EzkeySecurityRealm realm(String baseUrl) {
        EzkeySecurityRealm realm =
                new EzkeySecurityRealm(
                        baseUrl, "ikey", Secret.fromString(SECRET), "admin", Secret.fromString("ezkey-lab-admin"));
        realm.setEnabledUsers(List.of(new EzkeySecurityRealm.EzkeyAccount("labuser", "")));
        return realm;
    }

    private static HttpServer createServer(AtomicInteger creates, int attemptId, Integer challenge)
            throws Exception {
        return createServer(creates, attemptId, challenge, new CopyOnWriteArrayList<>());
    }

    private static HttpServer createServer(
            AtomicInteger creates, int attemptId, Integer challenge, List<Integer> cancelled) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v1/auth-attempts",
                exchange -> {
                    String method = exchange.getRequestMethod();
                    String path = exchange.getRequestURI().getPath();
                    exchange.getRequestBody().readAllBytes();
                    if ("POST".equals(method) && "/api/v1/auth-attempts".equals(path)) {
                        creates.incrementAndGet();
                        String challengeJson = challenge == null ? "null" : Integer.toString(challenge);
                        byte[] body =
                                ("{\"authAttemptId\":"
                                                + attemptId
                                                + ",\"authAttemptChallenge\":"
                                                + challengeJson
                                                + ",\"timeoutSeconds\":60,\"expiresAt\":\"t\"}")
                                        .getBytes(StandardCharsets.UTF_8);
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
                                "{\"status\":\"PENDING\",\"completed\":false,\"timeoutReached\":true}"
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

    private static HttpServer bodyCapturingServer(List<String> bodies, int challenge) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v1/auth-attempts",
                exchange -> {
                    byte[] raw = exchange.getRequestBody().readAllBytes();
                    if ("POST".equals(exchange.getRequestMethod())
                            && "/api/v1/auth-attempts".equals(exchange.getRequestURI().getPath())) {
                        bodies.add(new String(raw, StandardCharsets.UTF_8));
                        byte[] body =
                                ("{\"authAttemptId\":15,\"authAttemptChallenge\":"
                                                + challenge
                                                + ",\"timeoutSeconds\":60,\"expiresAt\":\"t\"}")
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(201, body.length);
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

    private static HttpServer waitStatusServer(String status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v1/auth-attempts",
                exchange -> {
                    String method = exchange.getRequestMethod();
                    String path = exchange.getRequestURI().getPath();
                    exchange.getRequestBody().readAllBytes();
                    if ("POST".equals(method) && "/api/v1/auth-attempts".equals(path)) {
                        byte[] body =
                                "{\"authAttemptId\":4,\"authAttemptChallenge\":12,\"timeoutSeconds\":60,\"expiresAt\":\"t\"}"
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
                                ("{\"status\":\"" + status + "\",\"completed\":true,\"timeoutReached\":false}")
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
}
