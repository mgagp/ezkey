/*
 * ezkey_conf.h — runtime conf init / key apply / file parse (testable).
 *
 * Ownership/mode checks stay in pam_ezkey.c; this module owns defaults,
 * bool parsing with fallback, and key=value application (#995 Patrick B1/m1).
 *
 * @since 2026
 */

#ifndef EZKEY_CONF_H
#define EZKEY_CONF_H

#include <stdio.h>

#include "ezkey_config.h"

#define EZKEY_CONF_URL_MAX 512
#define EZKEY_CONF_KEY_MAX 256
#define EZKEY_CONF_TITLE_MAX 200
#define EZKEY_CONF_MESSAGE_MAX 512
#define EZKEY_CONF_LINE_MAX 1024

/**
 * Runtime settings loaded from pam_ezkey.conf (and pam.d overrides).
 */
typedef struct {
    char api_url[EZKEY_CONF_URL_MAX];
    char integration_key[EZKEY_CONF_KEY_MAX];
    char secret_key[EZKEY_CONF_KEY_MAX];
    int wait_timeout;
    int wait_polling;
    int api_timeout;
    int challenge_requested;
    /* When 0 (default), only https:// Integration API URLs are accepted. */
    int lab_http;
    char context_title[EZKEY_CONF_TITLE_MAX + 1];
    char context_message[EZKEY_CONF_MESSAGE_MAX + 1];
    int debug;
    char config_file[EZKEY_CONF_URL_MAX];
} EzkeyConfig;

/**
 * Zero the struct and apply compiled-in defaults (including
 * EZKEY_DEFAULT_CHALLENGE_REQUESTED).
 *
 * @param cfg destination (must not be NULL)
 */
void ezkey_config_init(EzkeyConfig *cfg);

/**
 * Parse a conf boolean. Known true: true/1/yes/on. Known false: false/0/no/off.
 * Empty, NULL, or any other token → fallback (does not silently force false).
 *
 * @param value raw conf value (may be NULL)
 * @param fallback value when empty or unrecognized
 * @param unrecognized optional; set to 1 when fallback was used, else 0
 * @return 1, 0, or fallback
 */
int ezkey_parse_bool(const char *value, int fallback, int *unrecognized);

/**
 * Apply one conf key=value. Unrecognized bools keep the prior field value and
 * may emit an authpriv warning (challenge_requested typos).
 *
 * @param cfg config to update
 * @param key conf key
 * @param value conf value (trimmed by caller)
 */
void ezkey_conf_apply_key(EzkeyConfig *cfg, const char *key, const char *value);

/**
 * Parse key=value lines from an open stream into cfg (comments and blanks
 * skipped). Does not enforce ownership/mode — caller must.
 * A physical line longer than {@code EZKEY_CONF_LINE_MAX-2} payload characters
 * (1022 when {@code EZKEY_CONF_LINE_MAX} is 1024) — i.e. fgets fills the
 * buffer with no newline and more input remains — returns -3 after draining
 * to the next newline (#1016 C3/S5). Duplicate keys: last assignment wins.
 * The stack line buffer is wiped ({@code explicit_bzero}) before return
 * (#1016 S2).
 *
 * @param fp open text stream
 * @param cfg config already initialized (missing keys keep defaults)
 * @return 0 on success, -1 on I/O error, -3 on overlong line
 */
int ezkey_conf_load_fp(FILE *fp, EzkeyConfig *cfg);

#endif /* EZKEY_CONF_H */
