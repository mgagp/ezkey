/*
 * HTTP / JSON / URL guards harness (#1016 C1/C2/C4/C8).
 * Proves: body cap, empty PROXY, safe double→long, URL redaction.
 */

#define _POSIX_C_SOURCE 200809L

#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <curl/curl.h>

#include "ezkey_http.h"

static int failures;

static void expect_true(const char *name, int cond) {
    if (!cond) {
        fprintf(stderr, "[FAIL] %s\n", name);
        failures++;
    } else {
        printf("[PASS] %s\n", name);
    }
}

static void expect_int(const char *name, int got, int want) {
    if (got != want) {
        fprintf(stderr, "[FAIL] %s: got=%d want=%d\n", name, got, want);
        failures++;
    } else {
        printf("[PASS] %s (got=%d)\n", name, got);
    }
}

static void expect_long(const char *name, long got, long want) {
    if (got != want) {
        fprintf(stderr, "[FAIL] %s: got=%ld want=%ld\n", name, got, want);
        failures++;
    } else {
        printf("[PASS] %s (got=%ld)\n", name, got);
    }
}

/* C1: write callback refuses past EZKEY_HTTP_BODY_MAX. */
static void test_write_cb_cap(void) {
    EzkeyHttpBuf buf;
    char chunk[4096];
    size_t accepted = 0;
    size_t n;

    memset(chunk, 'A', sizeof(chunk));
    ezkey_http_buf_init(&buf);
    expect_true("C1: buf init", buf.data != NULL);

    while (accepted < EZKEY_HTTP_BODY_MAX) {
        size_t room = EZKEY_HTTP_BODY_MAX - accepted;
        size_t take = room < sizeof(chunk) ? room : sizeof(chunk);
        n = ezkey_http_write_cb(chunk, 1, take, &buf);
        if (n != take) {
            fprintf(stderr, "[FAIL] C1: early abort at accepted=%zu n=%zu\n",
                    accepted, n);
            failures++;
            ezkey_http_buf_free(&buf);
            return;
        }
        accepted += n;
    }
    expect_true("C1: filled exactly EZKEY_HTTP_BODY_MAX",
                buf.size == EZKEY_HTTP_BODY_MAX);
    n = ezkey_http_write_cb(chunk, 1, 1, &buf);
    expect_true("C1: one more byte → write_cb returns 0 (old unlimited would accept)",
                n == 0);
    expect_true("C1: size stayed at cap", buf.size == EZKEY_HTTP_BODY_MAX);

    /* Overflowing size*nmemb must abort. */
    n = ezkey_http_write_cb(chunk, (size_t)-1 / 2 + 1, 4, &buf);
    expect_true("C1: size*nmemb overflow → 0", n == 0);

    ezkey_http_buf_free(&buf);
}

/* C1: hostile endless chunked body via local stub + MAXFILESIZE/write_cb. */
static void test_hostile_endless_body(void) {
    int listen_fd = -1;
    struct sockaddr_in addr;
    socklen_t alen = sizeof(addr);
    pid_t child;
    char url[64];
    CURL *curl;
    EzkeyHttpBuf buf;
    CURLcode rc;
    long http = 0;

    listen_fd = socket(AF_INET, SOCK_STREAM, 0);
    expect_true("C1: listen socket", listen_fd >= 0);
    if (listen_fd < 0) {
        return;
    }
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = 0;
    if (bind(listen_fd, (struct sockaddr *)&addr, sizeof(addr)) != 0 ||
        listen(listen_fd, 1) != 0) {
        fprintf(stderr, "[FAIL] C1: bind/listen: %s\n", strerror(errno));
        failures++;
        close(listen_fd);
        return;
    }
    getsockname(listen_fd, (struct sockaddr *)&addr, &alen);
    snprintf(url, sizeof(url), "http://127.0.0.1:%u/",
             (unsigned)ntohs(addr.sin_port));

    child = fork();
    if (child < 0) {
        failures++;
        close(listen_fd);
        return;
    }
    if (child == 0) {
        int cfd;
        const char *hdr =
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n";
        char piece[1024];

        memset(piece, 'B', sizeof(piece));
        cfd = accept(listen_fd, NULL, NULL);
        if (cfd >= 0) {
            char req[256];
            ssize_t _nr = read(cfd, req, sizeof(req)); (void)_nr;
            ssize_t _nw = write(cfd, hdr, strlen(hdr)); (void)_nw;
            /* Endless chunks until peer closes (write_cb / MAXFILESIZE). */
            for (;;) {
                char clen[32];
                int m = snprintf(clen, sizeof(clen), "%zx\r\n", sizeof(piece));
                if (write(cfd, clen, (size_t)m) < 0) {
                    break;
                }
                if (write(cfd, piece, sizeof(piece)) < 0) {
                    break;
                }
                if (write(cfd, "\r\n", 2) < 0) {
                    break;
                }
            }
            close(cfd);
        }
        close(listen_fd);
        _exit(0);
    }
    close(listen_fd);

    ezkey_http_buf_init(&buf);
    curl = curl_easy_init();
    expect_true("C1: curl_easy_init", curl != NULL);
    if (curl != NULL) {
        expect_int("C1: transport guards",
                   ezkey_http_apply_transport_guards(curl), 0);
        curl_easy_setopt(curl, CURLOPT_URL, url);
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, ezkey_http_write_cb);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &buf);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 5L);
        rc = curl_easy_perform(curl);
        curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http);
        /*
         * Old unlimited callback would grow toward GBs until TIMEOUT.
         * With the cap we abort via WRITE_ERROR or FILESIZE_EXCEEDED and
         * keep the buffer ≤ EZKEY_HTTP_BODY_MAX.
         */
        expect_true("C1: hostile transfer did not succeed (CURLE_OK)",
                    rc != CURLE_OK);
        expect_true("C1: buffered body ≤ EZKEY_HTTP_BODY_MAX",
                    buf.size <= EZKEY_HTTP_BODY_MAX);
        expect_true("C1: aborted before filling multi-MB (≤ 64KiB)",
                    buf.size <= EZKEY_HTTP_BODY_MAX);
        curl_easy_cleanup(curl);
    }
    ezkey_http_buf_free(&buf);
    (void)waitpid(child, NULL, 0);
}

/*
 * C2: with http_proxy pointing at a loopback listener, CURLOPT_PROXY "" must
 * mean the listener receives nothing when we talk to a different URL.
 */
static void test_proxy_ignored(void) {
    int proxy_fd = -1;
    int origin_fd = -1;
    struct sockaddr_in paddr;
    struct sockaddr_in oaddr;
    socklen_t plen = sizeof(paddr);
    socklen_t olen = sizeof(oaddr);
    pid_t proxy_child;
    pid_t origin_child;
    char proxy_url[64];
    char origin_url[64];
    char envbuf[128];
    CURL *curl;
    EzkeyHttpBuf buf;
    CURLcode rc;
    int proxy_hits = 0;
    int status = 0;

    proxy_fd = socket(AF_INET, SOCK_STREAM, 0);
    origin_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (proxy_fd < 0 || origin_fd < 0) {
        failures++;
        return;
    }
    memset(&paddr, 0, sizeof(paddr));
    paddr.sin_family = AF_INET;
    paddr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    paddr.sin_port = 0;
    memset(&oaddr, 0, sizeof(oaddr));
    oaddr.sin_family = AF_INET;
    oaddr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    oaddr.sin_port = 0;
    if (bind(proxy_fd, (struct sockaddr *)&paddr, sizeof(paddr)) != 0 ||
        listen(proxy_fd, 1) != 0 ||
        bind(origin_fd, (struct sockaddr *)&oaddr, sizeof(oaddr)) != 0 ||
        listen(origin_fd, 1) != 0) {
        fprintf(stderr, "[FAIL] C2: bind: %s\n", strerror(errno));
        failures++;
        close(proxy_fd);
        close(origin_fd);
        return;
    }
    getsockname(proxy_fd, (struct sockaddr *)&paddr, &plen);
    getsockname(origin_fd, (struct sockaddr *)&oaddr, &olen);
    snprintf(proxy_url, sizeof(proxy_url), "http://127.0.0.1:%u",
             (unsigned)ntohs(paddr.sin_port));
    snprintf(origin_url, sizeof(origin_url), "http://127.0.0.1:%u/",
             (unsigned)ntohs(oaddr.sin_port));

    proxy_child = fork();
    if (proxy_child == 0) {
        struct timeval tv;
        fd_set rfds;
        int ready;

        tv.tv_sec = 2;
        tv.tv_usec = 0;
        FD_ZERO(&rfds);
        FD_SET(proxy_fd, &rfds);
        ready = select(proxy_fd + 1, &rfds, NULL, NULL, &tv);
        _exit(ready > 0 ? 1 : 0); /* 1 = got a connection (BAD) */
    }
    origin_child = fork();
    if (origin_child == 0) {
        int cfd = accept(origin_fd, NULL, NULL);
        if (cfd >= 0) {
            const char *resp =
                "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok";
            char req[512];
            ssize_t _nr = read(cfd, req, sizeof(req)); (void)_nr;
            ssize_t _nw2 = write(cfd, resp, strlen(resp)); (void)_nw2;
            close(cfd);
        }
        _exit(0);
    }
    close(proxy_fd);
    close(origin_fd);

    snprintf(envbuf, sizeof(envbuf), "%s", proxy_url);
    setenv("http_proxy", envbuf, 1);
    setenv("HTTP_PROXY", envbuf, 1);
    setenv("https_proxy", envbuf, 1);
    setenv("all_proxy", envbuf, 1);

    ezkey_http_buf_init(&buf);
    curl = curl_easy_init();
    expect_true("C2: curl init", curl != NULL);
    if (curl != NULL) {
        expect_int("C2: apply PROXY \"\"",
                   ezkey_http_apply_transport_guards(curl), 0);
        curl_easy_setopt(curl, CURLOPT_URL, origin_url);
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, ezkey_http_write_cb);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &buf);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 3L);
        curl_easy_setopt(curl, CURLOPT_USERPWD, "ikey:skey");
        curl_easy_setopt(curl, CURLOPT_HTTPAUTH, CURLAUTH_BASIC);
        rc = curl_easy_perform(curl);
        expect_int("C2: direct origin CURLE_OK (proxy ignored)", (int)rc,
                   (int)CURLE_OK);
        expect_true("C2: body from origin",
                    buf.data != NULL && strstr(buf.data, "ok") != NULL);
        curl_easy_cleanup(curl);
    }
    ezkey_http_buf_free(&buf);

    unsetenv("http_proxy");
    unsetenv("HTTP_PROXY");
    unsetenv("https_proxy");
    unsetenv("all_proxy");

    (void)waitpid(proxy_child, &status, 0);
    proxy_hits = WIFEXITED(status) ? WEXITSTATUS(status) : 2;
    expect_int("C2: proxy listener received nothing (old: would get CONNECT/GET)",
               proxy_hits, 0);
    (void)waitpid(origin_child, NULL, 0);
}

/* C4: safe double→long. */
static void test_json_double_to_long(void) {
    long out = 0;

    expect_int("C4: 42 ok", ezkey_json_double_to_long(42.0, 1, 100, &out), 0);
    expect_long("C4: 42 value", out, 42);
    expect_int("C4: 1e300 reject",
               ezkey_json_double_to_long(1e300, 1, (long)(1LL << 53), &out),
               -1);
    expect_int("C4: -1 reject for id",
               ezkey_json_double_to_long(-1.0, 1, 100, &out), -1);
    expect_int("C4: 0 reject for id",
               ezkey_json_double_to_long(0.0, 1, 100, &out), -1);
    expect_int("C4: 0 ok for challenge",
               ezkey_json_double_to_long(0.0, 0, 99, &out), 0);
    expect_long("C4: challenge 0", out, 0);
    expect_int("C4: 99.5 fractional reject",
               ezkey_json_double_to_long(99.5, 0, 99, &out), -1);
    expect_int("C4: NaN reject",
               ezkey_json_double_to_long(0.0 / 0.0, 0, 99, &out), -1);
    expect_int("C4: +Inf reject",
               ezkey_json_double_to_long(1.0 / 0.0, 0, 99, &out), -1);
    expect_int("C4: 100 reject challenge",
               ezkey_json_double_to_long(100.0, 0, 99, &out), -1);
}

/* C8/S3: any '@' → scheme://- (no password-prefix leak on unencoded /). */
static void test_url_for_log(void) {
    char dest[256];

    expect_int("C8/S3: userinfo → scheme://-",
               ezkey_url_for_log(
                   dest, sizeof(dest),
                   "https://ikey:s3cret@integration.example:8443/api/v1"),
               0);
    expect_true("C8/S3: scheme://- only", strcmp(dest, "https://-") == 0);
    expect_true("C8/S3: no userinfo", strstr(dest, "ikey") == NULL);
    expect_true("C8/S3: no secret", strstr(dest, "s3cret") == NULL);

    /* Old parser truncated at '/' in password → leaked "http://bob:SUPER". */
    expect_int("S3: password with unencoded /",
               ezkey_url_for_log(dest, sizeof(dest),
                                 "http://bob:SUPER/SECRETPW@h/path"),
               0);
    expect_true("S3: result is http://-", strcmp(dest, "http://-") == 0);
    expect_true("S3: no bob", strstr(dest, "bob") == NULL);
    expect_true("S3: no SUPER prefix", strstr(dest, "SUPER") == NULL);
    expect_true("S3: no SECRETPW", strstr(dest, "SECRETPW") == NULL);

    expect_int("C8: plain http (no @)",
               ezkey_url_for_log(dest, sizeof(dest),
                                 "http://integration-api:7080/"),
               0);
    expect_true("C8: host only",
                strcmp(dest, "http://integration-api:7080") == 0);
}

/* S1: SSLKEYLOGFILE detection (pam_sm refuses before network when set). */
static void test_sslkeylogfile_env(void) {
    unsetenv("SSLKEYLOGFILE");
    expect_int("S1: unset → 0", ezkey_env_sslkeylogfile_set(), 0);
    setenv("SSLKEYLOGFILE", "/tmp/ezkey-should-not-create.keys", 1);
    expect_int("S1: set → 1 (old: would write TLS keys as root)",
               ezkey_env_sslkeylogfile_set(), 1);
    unsetenv("SSLKEYLOGFILE");
    expect_int("S1: cleared → 0", ezkey_env_sslkeylogfile_set(), 0);
}

int main(void) {
    printf("=== ezkey_http harness (#1016 C1/C2/C4/C8 + S1/S3) ===\n");
    curl_global_init(CURL_GLOBAL_DEFAULT);
    test_write_cb_cap();
    test_hostile_endless_body();
    test_proxy_ignored();
    test_json_double_to_long();
    test_url_for_log();
    test_sslkeylogfile_env();
    curl_global_cleanup();
    if (failures != 0) {
        fprintf(stderr, "=== Results: %d failed ===\n", failures);
        return 1;
    }
    printf("=== Results: all passed ===\n");
    return 0;
}
