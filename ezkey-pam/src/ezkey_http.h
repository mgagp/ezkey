/*
 * ezkey_http.h — bounded response buffer + libcurl transport guards (#1016 C1/C2).
 *
 * @since 2026
 */

#ifndef EZKEY_HTTP_H
#define EZKEY_HTTP_H

#include <stddef.h>

#include <curl/curl.h>

#ifdef __cplusplus
extern "C" {
#endif

/** Hard cap on Integration API response bodies (create / wait / cancel). */
#define EZKEY_HTTP_BODY_MAX (64 * 1024)

/**
 * Growable response buffer for CURLOPT_WRITEFUNCTION.
 */
typedef struct {
    char *data;
    size_t size;
} EzkeyHttpBuf;

/**
 * Allocate a 1-byte empty buffer (NUL). On OOM, data is NULL and size is 0.
 *
 * @param buf destination (must not be NULL)
 */
void ezkey_http_buf_init(EzkeyHttpBuf *buf);

/**
 * Free buffer contents and reset size.
 *
 * @param buf buffer (may be NULL)
 */
void ezkey_http_buf_free(EzkeyHttpBuf *buf);

/**
 * libcurl write callback: append at most until EZKEY_HTTP_BODY_MAX.
 * Returns 0 (abort transfer) when the next chunk would exceed the cap,
 * on OOM, or on size*nmemb overflow — caller maps that to UNAVAIL.
 *
 * @param contents chunk pointer
 * @param size curl size
 * @param nmemb curl nmemb
 * @param userp EzkeyHttpBuf *
 * @return bytes taken, or 0 to abort
 */
size_t ezkey_http_write_cb(void *contents, size_t size, size_t nmemb,
                           void *userp);

/**
 * Apply fail-closed transport guards on a curl easy handle:
 * CURLOPT_PROXY "" (ignore http_proxy/https_proxy/ALL_PROXY),
 * CURLOPT_MAXFILESIZE EZKEY_HTTP_BODY_MAX,
 * CURLOPT_NETRC CURL_NETRC_IGNORED,
 * CURLOPT_NOSIGNAL 1.
 *
 * @param curl easy handle (must not be NULL)
 * @return 0 on success, -1 if any setopt fails
 */
int ezkey_http_apply_transport_guards(CURL *curl);

/**
 * Convert a JSON number (IEEE double) to long only when finite, integral,
 * and inside [lo, hi] inclusive. Rejects NaN/Inf, fractions, and values that
 * would be undefined as a C cast (e.g. 1e300).
 *
 * @param d valuedouble from cJSON
 * @param lo inclusive minimum
 * @param hi inclusive maximum (must be <= 2^53 for exactness)
 * @param out receives the long on success
 * @return 0 on success, -1 on reject
 */
int ezkey_json_double_to_long(double d, long lo, long hi, long *out);

/**
 * Format scheme://host[:port] for debug logs — strips userinfo and path/query.
 * Output is also passed through control-char neutralization.
 *
 * @param dest destination
 * @param dest_size capacity
 * @param url raw Integration API URL (may contain embedded credentials)
 * @return 0 on success, -1 if dest/url invalid (dest set to "-" when possible)
 */
int ezkey_url_for_log(char *dest, size_t dest_size, const char *url);

#ifdef __cplusplus
}
#endif

#endif /* EZKEY_HTTP_H */
