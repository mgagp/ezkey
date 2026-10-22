package org.ezkey.jenkins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.Secret;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Minimal JenkinsRule HTTP proofs for send/confirm CSRF and method constraints (#972 Patrick B2).
 *
 * <p>One JenkinsRule per class method (all asserts in a single test) to avoid Jetty re-bind races
 * inside Docker CI.
 *
 * @since 2026
 */
@WithJenkins
class EzkeyLabLoginHttpTest {

    @Test
    void sendRequiresPostAndCrumb(JenkinsRule jenkins) throws Exception {
        EzkeyMockSecurityRealm realm =
                new EzkeyMockSecurityRealm(
                        "http://127.0.0.1:9",
                        "ikey",
                        Secret.fromString("skey"),
                        "admin",
                        Secret.fromString("break-glass"));
        jenkins.jenkins.setSecurityRealm(realm);
        assertTrue(jenkins.jenkins.getCrumbIssuer() != null, "crumb issuer must be active");

        assertEquals(
                405,
                http(jenkins, "ezkey-lab-login/send", "GET", null),
                "GET send must be rejected (@RequirePOST)");
        assertEquals(
                405,
                http(jenkins, "ezkey-lab-login/confirm", "GET", null),
                "GET confirm must be rejected (@RequirePOST)");
        assertEquals(
                403,
                http(jenkins, "ezkey-lab-login/send", "POST", "username=labuser"),
                "POST without crumb must be forbidden by CrumbFilter");
    }

    private static int http(JenkinsRule jenkins, String path, String method, String formBody)
            throws Exception {
        URL url = new URL(jenkins.getURL(), path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
        if (formBody != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] body = formBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
        }
        return conn.getResponseCode();
    }
}
