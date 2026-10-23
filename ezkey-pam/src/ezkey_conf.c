/*
 * ezkey_conf.c — conf defaults, bool parse with fallback, key apply, load.
 */

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
    cfg->lab_http = 0;
    cfg->debug = 0;
    strncpy(cfg->context_title, "SSH login", sizeof(cfg->context_title) - 1);
    strncpy(cfg->config_file, EZKEY_DEFAULT_CONFIG_FILE,
            sizeof(cfg->config_file) - 1);
#if EZKEY_DEBUG
    cfg->debug = 1;
#endif
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

    if (cfg == NULL || key == NULL) {
        return;
    }
    if (strcmp(key, "integration_api_url") == 0 ||
        strcmp(key, "api_url") == 0) {
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
            syslog(LOG_AUTHPRIV | LOG_WARNING,
                   "pam_ezkey: challenge_requested value unrecognized "
                   "(%s); keeping %s",
                   (value != NULL && value[0] != '\0') ? value : "(empty)",
                   cfg->challenge_requested ? "true" : "false");
        }
        cfg->challenge_requested = parsed;
    } else if (strcmp(key, "lab_http") == 0) {
        parsed = ezkey_parse_bool(value, cfg->lab_http, &unrecognized);
        if (unrecognized) {
            syslog(LOG_AUTHPRIV | LOG_WARNING,
                   "pam_ezkey: lab_http value unrecognized (%s); keeping %s",
                   (value != NULL && value[0] != '\0') ? value : "(empty)",
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
                   "pam_ezkey: debug value unrecognized (%s); keeping %s",
                   (value != NULL && value[0] != '\0') ? value : "(empty)",
                   cfg->debug ? "true" : "false");
        }
        cfg->debug = parsed;
    }
}

int ezkey_conf_load_fp(FILE *fp, EzkeyConfig *cfg) {
    char line[EZKEY_CONF_LINE_MAX];

    if (fp == NULL || cfg == NULL) {
        errno = EINVAL;
        return -1;
    }
    while (fgets(line, sizeof(line), fp) != NULL) {
        char *hash;
        char *eq;
        char *key;
        char *value;

        hash = strchr(line, '#');
        if (hash != NULL) {
            *hash = '\0';
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
        return -1;
    }
    return 0;
}
