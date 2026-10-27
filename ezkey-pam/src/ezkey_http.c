/*
 * ezkey_http.c — bounded curl body + transport guards + safe JSON longs (#1016).
 */

#define _POSIX_C_SOURCE 200809L

#include "ezkey_http.h"

#include <ctype.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

#include "ezkey_sanitize.h"

void ezkey_http_buf_init(EzkeyHttpBuf *buf) {
    if (buf == NULL) {
        return;
    }
    buf->data = malloc(1);
    if (buf->data != NULL) {
        buf->data[0] = '\0';
        buf->size = 0;
    } else {
        buf->size = 0;
    }
}

void ezkey_http_buf_free(EzkeyHttpBuf *buf) {
    if (buf == NULL) {
        return;
    }
    if (buf->data != NULL) {
        free(buf->data);
        buf->data = NULL;
    }
    buf->size = 0;
}

size_t ezkey_http_write_cb(void *contents, size_t size, size_t nmemb,
                           void *userp) {
    EzkeyHttpBuf *buf = (EzkeyHttpBuf *)userp;
    size_t realsize;
    char *ptr;

    if (buf == NULL || buf->data == NULL || contents == NULL) {
        return 0;
    }
    /* Overflow-safe size * nmemb. */
    if (size != 0 && nmemb > (SIZE_MAX / size)) {
        return 0;
    }
    realsize = size * nmemb;
    if (realsize > EZKEY_HTTP_BODY_MAX ||
        buf->size > EZKEY_HTTP_BODY_MAX - realsize) {
        /* Cap exceeded — abort transfer (libcurl → CURLE_WRITE_ERROR). */
        return 0;
    }
    ptr = realloc(buf->data, buf->size + realsize + 1);
    if (ptr == NULL) {
        return 0;
    }
    buf->data = ptr;
    memcpy(&(buf->data[buf->size]), contents, realsize);
    buf->size += realsize;
    buf->data[buf->size] = '\0';
    return realsize;
}

int ezkey_env_sslkeylogfile_set(void) {
    const char *v = getenv("SSLKEYLOGFILE");

    return (v != NULL) ? 1 : 0;
}

int ezkey_http_apply_transport_guards(CURL *curl) {
    CURLcode rc;

    if (curl == NULL) {
        return -1;
    }
    /*
     * Empty PROXY disables http_proxy / https_proxy / ALL_PROXY / no_proxy
     * inheritance from the process environment (#1016 C2). Without this,
     * Basic credentials ride to a caller-controlled proxy in lab http:// mode.
     */
    rc = curl_easy_setopt(curl, CURLOPT_PROXY, "");
    if (rc != CURLE_OK) {
        return -1;
    }
    rc = curl_easy_setopt(curl, CURLOPT_MAXFILESIZE,
                          (long)EZKEY_HTTP_BODY_MAX);
    if (rc != CURLE_OK) {
        return -1;
    }
    rc = curl_easy_setopt(curl, CURLOPT_NETRC, (long)CURL_NETRC_IGNORED);
    if (rc != CURLE_OK) {
        return -1;
    }
    rc = curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
    if (rc != CURLE_OK) {
        return -1;
    }
    /* #1016 P3: pin TLS verify (do not rely on libcurl defaults alone). */
    rc = curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
    if (rc != CURLE_OK) {
        return -1;
    }
    rc = curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);
    if (rc != CURLE_OK) {
        return -1;
    }
    return 0;
}

int ezkey_json_double_to_long(double d, long lo, long hi, long *out) {
    long v;

    if (out == NULL || lo > hi) {
        return -1;
    }
    if (!isfinite(d)) {
        return -1;
    }
    /* Exact integer range for IEEE-754 doubles ends at 2^53. */
    if (d < (double)lo || d > (double)hi) {
        return -1;
    }
    v = (long)d;
    if ((double)v != d) {
        return -1; /* fractional */
    }
    if (v < lo || v > hi) {
        return -1;
    }
    *out = v;
    return 0;
}

int ezkey_url_for_log(char *dest, size_t dest_size, const char *url) {
    const char *scheme_end;
    const char *auth;
    const char *host;
    const char *path;
    char raw[512];
    size_t scheme_len;
    size_t host_len;
    size_t n;

    if (dest == NULL || dest_size == 0) {
        return -1;
    }
    if (url == NULL || url[0] == '\0') {
        dest[0] = '-';
        if (dest_size > 1) {
            dest[1] = '\0';
        }
        return -1;
    }
    scheme_end = strstr(url, "://");
    if (scheme_end == NULL || scheme_end == url) {
        ezkey_sanitize_for_log(dest, dest_size, "-");
        return -1;
    }
    scheme_len = (size_t)(scheme_end - url);
    /*
     * #1016 S3: any '@' means userinfo (or a password containing unencoded
     * / ? # that truncates authority parsing and leaks a password prefix).
     * Log only scheme://- — never attempt to recover host from userinfo URLs.
     */
    if (strchr(url, '@') != NULL) {
        if (scheme_len + 4 >= sizeof(raw)) {
            ezkey_sanitize_for_log(dest, dest_size, "-");
            return -1;
        }
        memcpy(raw, url, scheme_len);
        memcpy(raw + scheme_len, "://-", 5); /* includes NUL */
        ezkey_sanitize_for_log(dest, dest_size, raw);
        return 0;
    }
    auth = scheme_end + 3;
    host = auth;
    path = auth;
    while (*path != '\0' && *path != '/' && *path != '?' && *path != '#') {
        path++;
    }
    host_len = (size_t)(path - host);
    if (scheme_len == 0 || host_len == 0 ||
        scheme_len + 3 + host_len >= sizeof(raw)) {
        ezkey_sanitize_for_log(dest, dest_size, "-");
        return -1;
    }
    n = 0;
    memcpy(raw + n, url, scheme_len);
    n += scheme_len;
    raw[n++] = ':';
    raw[n++] = '/';
    raw[n++] = '/';
    memcpy(raw + n, host, host_len);
    n += host_len;
    raw[n] = '\0';
    ezkey_sanitize_for_log(dest, dest_size, raw);
    return 0;
}
