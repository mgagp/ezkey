/*
 * ezkey_conf.c — conf defaults, bool parse with fallback, key apply, load.
 */

#define _DEFAULT_SOURCE
#define _POSIX_C_SOURCE 200809L

#include "ezkey_conf.h"

#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <syslog.h>

#include "ezkey_budget.h"
#include "ezkey_config.h"
#include "ezkey_sanitize.h"

static void ezkey_conf_secure_wipe(void *ptr, size_t len) {
    if (ptr == NULL || len == 0) {
        return;
    }
#if defined(__GLIBC__) || defined(__FreeBSD__) || defined(__OpenBSD__)
    explicit_bzero(ptr, len);
#else
    {
        volatile unsigned char *p = (volatile unsigned char *)ptr;
        while (len-- > 0) {
            *p++ = 0;
        }
    }
#endif
}

static void set_cfg_str(char *dest, size_t dest_size, const char *src) {
    if (dest == NULL || dest_size == 0) {
        return;
    }
    if (src == NULL) {
        dest[0] = '\0';
        return;
    }
    strncpy(dest, src, dest_size - 1);
    dest[dest_size - 1] = '\0';
}

static void trim_inplace(char *s) {
    char *start;
    char *end;
    size_t len;

    if (s == NULL) {
        return;
    }
    start = s;
    while (*start != '\0' && isspace((unsigned char)*start)) {
        start++;
    }
    if (start != s) {
        memmove(s, start, strlen(start) + 1);
    }
    len = strlen(s);
    end = s + len;
    while (end > s && isspace((unsigned char)*(end - 1))) {
        end--;
    }
    *end = '\0';
}

static int parse_positive_int(const char *value, int fallback) {
    char *end = NULL;
    long parsed;

    if (value == NULL || *value == '\0') {
        return fallback;
    }
    errno = 0;
    parsed = strtol(value, &end, 10);
    if (errno != 0 || end == value || *end != '\0' || parsed <= 0 ||
        parsed > 86400) {
        return fallback;
    }
    return (int)parsed;
}

void ezkey_config_init(EzkeyConfig *cfg) {
    if (cfg == NULL) {
        return;
    }
    memset(cfg, 0, sizeof(*cfg));
    /* api_url left empty — must come from conf (no localhost default; B1). */
    cfg->wait_timeout = EZKEY_DEFAULT_WAIT_TIMEOUT;
    cfg->wait_polling = EZKEY_DEFAULT_WAIT_POLLING;
    cfg->api_timeout = EZKEY_DEFAULT_API_TIMEOUT;
    cfg->challenge_requested = EZKEY_DEFAULT_CHALLENGE_REQUESTED;
    /* #1016 P4: lab_http default must stay 0 (http:// fail-closed). */
    cfg->lab_http = 0;
    cfg->debug = 0;
    strncpy(cfg->context_title, "SSH login", sizeof(cfg->context_title) - 1);
    strncpy(cfg->config_file, EZKEY_DEFAULT_CONFIG_FILE,
            sizeof(cfg->config_file) - 1);
#if EZKEY_DEBUG
    cfg->debug = 1;
#endif
}

void ezkey_config_wipe_secrets(EzkeyConfig *cfg) {
    if (cfg == NULL) {
        return;
    }
    ezkey_conf_secure_wipe(cfg->integration_key, sizeof(cfg->integration_key));
    ezkey_conf_secure_wipe(cfg->secret_key, sizeof(cfg->secret_key));
}

int ezkey_parse_bool(const char *value, int fallback, int *unrecognized) {
    if (unrecognized != NULL) {
        *unrecognized = 0;
    }
    if (value == NULL || *value == '\0') {
        if (unrecognized != NULL) {
            *unrecognized = 1;
        }
        return fallback;
    }
    if (strcasecmp(value, "true") == 0 || strcmp(value, "1") == 0 ||
        strcasecmp(value, "yes") == 0 || strcasecmp(value, "on") == 0) {
        return 1;
    }
    if (strcasecmp(value, "false") == 0 || strcmp(value, "0") == 0 ||
        strcasecmp(value, "no") == 0 || strcasecmp(value, "off") == 0) {
        return 0;
    }
    if (unrecognized != NULL) {
        *unrecognized = 1;
    }
    return fallback;
}

void ezkey_conf_apply_key(EzkeyConfig *cfg, const char *key,
                          const char *value) {
    int unrecognized = 0;
    int parsed;
    int known = 1;

    if (cfg == NULL || key == NULL) {
        return;
    }
    if (strcmp(key, "integration_api_url") == 0 ||
        strcmp(key, "api_url") == 0) {
        /* api_url is a documented alias of integration_api_url (#1016 P7). */
        set_cfg_str(cfg->api_url, sizeof(cfg->api_url), value);
    } else if (strcmp(key, "integration_key") == 0) {
        set_cfg_str(cfg->integration_key, sizeof(cfg->integration_key), value);
    } else if (strcmp(key, "secret_key") == 0) {
        set_cfg_str(cfg->secret_key, sizeof(cfg->secret_key), value);
    } else if (strcmp(key, "wait_timeout") == 0) {
        cfg->wait_timeout = ezkey_clamp_wait_timeout(
            parse_positive_int(value, cfg->wait_timeout));
    } else if (strcmp(key, "wait_polling") == 0) {
        cfg->wait_polling = parse_positive_int(value, cfg->wait_polling);
    } else if (strcmp(key, "api_timeout") == 0) {
        cfg->api_timeout = parse_positive_int(value, cfg->api_timeout);
        if (cfg->api_timeout > EZKEY_MAX_API_TIMEOUT) {
            cfg->api_timeout = EZKEY_MAX_API_TIMEOUT;
        }
    } else if (strcmp(key, "challenge_requested") == 0) {
        parsed =
            ezkey_parse_bool(value, cfg->challenge_requested, &unrecognized);
        if (unrecognized) {
            /* #1054: never log the boolean key's value (may be hostile). */
            syslog(LOG_AUTHPRIV | LOG_WARNING,
                   "pam_ezkey: challenge_requested value unrecognized; "
                   "keeping %s",
                   cfg->challenge_requested ? "true" : "false");
        }
        cfg->challenge_requested = parsed;
    } else if (strcmp(key, "lab_http") == 0) {
        parsed = ezkey_parse_bool(value, cfg->lab_http, &unrecognized);
        if (unrecognized) {
            syslog(LOG_AUTHPRIV | LOG_WARNING,
                   "pam_ezkey: lab_http value unrecognized; keeping %s",
                   cfg->lab_http ? "true" : "false");
        }
        cfg->lab_http = parsed;
    } else if (strcmp(key, "context_title") == 0) {
        set_cfg_str(cfg->context_title, sizeof(cfg->context_title), value);
    } else if (strcmp(key, "context_message") == 0) {
        set_cfg_str(cfg->context_message, sizeof(cfg->context_message), value);
    } else if (strcmp(key, "debug") == 0) {
        parsed = ezkey_parse_bool(value, cfg->debug, &unrecognized);
        if (unrecognized) {
            syslog(LOG_AUTHPRIV | LOG_WARNING,
                   "pam_ezkey: debug value unrecognized; keeping %s",
                   cfg->debug ? "true" : "false");
        }
        cfg->debug = parsed;
    } else {
        known = 0;
    }
    /* #1016 P6 / #1054: warn on unknown keys — sanitized name only, never value. */
    if (!known) {
        char key_safe[96];

        ezkey_sanitize_for_log(key_safe, sizeof(key_safe), key);
        syslog(LOG_AUTHPRIV | LOG_WARNING,
               "pam_ezkey: reason=conf_unknown_key key=%s", key_safe);
    }
}

int ezkey_conf_load_fp(FILE *fp, EzkeyConfig *cfg) {
    char line[EZKEY_CONF_LINE_MAX];
    int rc = 0;

    if (fp == NULL || cfg == NULL) {
        errno = EINVAL;
        return -1;
    }
    while (fgets(line, sizeof(line), fp) != NULL) {
        char *hash;
        char *eq;
        char *key;
        char *value;
        char *nl;

        /*
         * #1016 C3/S5: fgets(size=EZKEY_CONF_LINE_MAX) stores at most
         * EZKEY_CONF_LINE_MAX-1 payload bytes. A physical line of 1023 chars
         * without a newline fills the buffer and is rejected when more input
         * remains; the largest accepted line body is therefore 1022 chars
         * (plus newline). Drain and return -3 so a truncated comment tail
         * cannot become a live key (e.g. lab_http=true).
         */
        nl = strchr(line, '\n');
        if (nl == NULL && !feof(fp)) {
            int ch;

            while ((ch = fgetc(fp)) != EOF && ch != '\n') {
                /* drain */
            }
            errno = EMSGSIZE;
            rc = -3;
            goto out;
        }

        /*
         * #1016 P6: '#' starts a comment only at line start or after
         * whitespace, so secret_key=ab#cd is not silently truncated. Full-line
         * comments (# ...) still work. Values must not rely on mid-token '#'.
         */
        hash = line;
        while ((hash = strchr(hash, '#')) != NULL) {
            if (hash == line || isspace((unsigned char)*(hash - 1))) {
                *hash = '\0';
                break;
            }
            hash++;
        }
        trim_inplace(line);
        if (line[0] == '\0') {
            continue;
        }
        eq = strchr(line, '=');
        if (eq == NULL) {
            continue;
        }
        *eq = '\0';
        key = line;
        value = eq + 1;
        trim_inplace(key);
        trim_inplace(value);
        if (*key == '\0') {
            continue;
        }
        ezkey_conf_apply_key(cfg, key, value);
    }
    if (ferror(fp)) {
        rc = -1;
        goto out;
    }
    rc = 0;
out:
    /* #1016 S2: wipe stack line buffer (may have held secret_key=…). */
    ezkey_conf_secure_wipe(line, sizeof(line));
    return rc;
}
