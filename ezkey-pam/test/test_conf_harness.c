/*
 * Harness: conf defaults + bool parse fallback (#995 Patrick B1 / m1).
 * Loads a conf stream WITHOUT challenge_requested and asserts the compiled-in
 * default remains true; typos keep the prior value.
 */

#include "ezkey_conf.h"
#include "ezkey_config.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int fails;

static void expect_true(const char *name, int cond) {
    if (cond) {
        printf("[PASS] %s\n", name);
    } else {
        printf("[FAIL] %s\n", name);
        fails++;
    }
}

static void expect_eq(const char *name, int got, int want) {
    if (got == want) {
        printf("[PASS] %s (got=%d)\n", name, got);
    } else {
        printf("[FAIL] %s (got=%d want=%d)\n", name, got, want);
        fails++;
    }
}

int main(void) {
    EzkeyConfig cfg;
    FILE *fp;
    int unrecognized = 0;
    const char *no_chal_key =
        "integration_api_url=https://example.invalid\n"
        "wait_timeout=30\n"
        "lab_http=false\n"
        "debug=false\n";

    printf("=== ezkey_conf harness (#995 B1/m1) ===\n");

    expect_eq("compiled default EZKEY_DEFAULT_CHALLENGE_REQUESTED",
              EZKEY_DEFAULT_CHALLENGE_REQUESTED, 1);

    ezkey_config_init(&cfg);
    expect_eq("ezkey_config_init → challenge_requested true",
              cfg.challenge_requested, 1);
    expect_eq("P4: lab_http default 0 after init", cfg.lab_http, 0);

    /* Prefer B1: load conf without the key; default must stick. */
    {
        char path[] = "/tmp/ezkey-conf-harness-XXXXXX";
        int fd = mkstemp(path);

        expect_true("mkstemp conf without challenge_requested", fd >= 0);
        if (fd >= 0) {
            if (write(fd, no_chal_key, strlen(no_chal_key)) < 0) {
                expect_true("write conf fixture", 0);
            }
            close(fd);
            fp = fopen(path, "r");
            expect_true("fopen conf fixture", fp != NULL);
            if (fp != NULL) {
                expect_eq("ezkey_conf_load_fp ok", ezkey_conf_load_fp(fp, &cfg),
                          0);
                fclose(fp);
                expect_eq(
                    "missing challenge_requested key → still true (init default)",
                    cfg.challenge_requested, 1);
                expect_eq("wait_timeout applied from conf without chal key",
                          cfg.wait_timeout, 30);
            }
            unlink(path);
        }
    }

    /* m1: known true / false */
    expect_eq("parse_bool true", ezkey_parse_bool("true", 0, &unrecognized), 1);
    expect_eq("parse_bool true recognized", unrecognized, 0);
    expect_eq("parse_bool false", ezkey_parse_bool("false", 1, &unrecognized),
              0);
    expect_eq("parse_bool false recognized", unrecognized, 0);
    expect_eq("parse_bool off", ezkey_parse_bool("off", 1, &unrecognized), 0);

    /* m1: empty / typo keep fallback (do not silently disable challenge). */
    expect_eq("parse_bool empty → fallback 1",
              ezkey_parse_bool("", 1, &unrecognized), 1);
    expect_eq("parse_bool empty unrecognized", unrecognized, 1);
    expect_eq("parse_bool NULL → fallback 1",
              ezkey_parse_bool(NULL, 1, &unrecognized), 1);
    expect_eq("parse_bool typo → fallback 1",
              ezkey_parse_bool("ture", 1, &unrecognized), 1);
    expect_eq("parse_bool typo unrecognized", unrecognized, 1);
    expect_eq("parse_bool typo → fallback 0",
              ezkey_parse_bool("maybe", 0, &unrecognized), 0);

    ezkey_config_init(&cfg);
    ezkey_conf_apply_key(&cfg, "challenge_requested", "yesplease");
    expect_eq("apply typo challenge_requested keeps default true",
              cfg.challenge_requested, 1);
    ezkey_conf_apply_key(&cfg, "challenge_requested", "false");
    expect_eq("apply false challenge_requested → 0", cfg.challenge_requested,
              0);
    ezkey_conf_apply_key(&cfg, "challenge_requested", "");
    expect_eq("apply empty challenge_requested keeps prior false",
              cfg.challenge_requested, 0);
    ezkey_conf_apply_key(&cfg, "challenge_requested", "true");
    expect_eq("apply true challenge_requested → 1", cfg.challenge_requested, 1);

    /*
     * #1016 C3: overlong line without newline before buffer fills — old parser
     * would treat the tail as a new line (comment tail → lab_http=true).
     */
    {
        char path[] = "/tmp/ezkey-conf-longline-XXXXXX";
        int fd = mkstemp(path);
        char longline[EZKEY_CONF_LINE_MAX + 64];
        size_t i;

        expect_true("C3: mkstemp long-line fixture", fd >= 0);
        if (fd >= 0) {
            memset(longline, 'x', sizeof(longline));
            /* Comment prefix then filler, then a dangerous tail after the cut. */
            memcpy(longline, "# ", 2);
            /* Ensure no newline in the first EZKEY_CONF_LINE_MAX-1 bytes. */
            for (i = 2; i < (size_t)EZKEY_CONF_LINE_MAX - 1; i++) {
                longline[i] = 'c';
            }
            /* Tail that old fgets split would parse as a live key. */
            memcpy(longline + (EZKEY_CONF_LINE_MAX - 1),
                   "\nlab_http=true\n", 15);
            if (write(fd, longline,
                      (EZKEY_CONF_LINE_MAX - 1) + 15) < 0) {
                expect_true("C3: write long-line fixture", 0);
            }
            close(fd);
            ezkey_config_init(&cfg);
            expect_eq("C3: lab_http default 0 before load", cfg.lab_http, 0);
            fp = fopen(path, "r");
            expect_true("C3: fopen long-line", fp != NULL);
            if (fp != NULL) {
                int rc = ezkey_conf_load_fp(fp, &cfg);
                fclose(fp);
                expect_eq("C3: overlong line → -3 (old: 0 + lab_http applied)",
                          rc, -3);
                expect_eq("C3: lab_http still 0 (not fail-open from comment tail)",
                          cfg.lab_http, 0);
            }
            unlink(path);
        }
    }

    /* Final line without newline at EOF must still load (not -3). */
    {
        char path[] = "/tmp/ezkey-conf-eof-XXXXXX";
        int fd = mkstemp(path);
        const char *body = "lab_http=false";

        expect_true("C3: mkstemp EOF-no-nl", fd >= 0);
        if (fd >= 0) {
            (void)write(fd, body, strlen(body));
            close(fd);
            ezkey_config_init(&cfg);
            fp = fopen(path, "r");
            if (fp != NULL) {
                expect_eq("C3: EOF without newline → 0",
                          ezkey_conf_load_fp(fp, &cfg), 0);
                fclose(fp);
                expect_eq("C3: lab_http=false applied", cfg.lab_http, 0);
            }
            unlink(path);
        }
    }

    /* P10: apply arms for keys + api_timeout clamp + lab_http typo keep. */
    ezkey_config_init(&cfg);
    ezkey_conf_apply_key(&cfg, "integration_key", "ikey-test");
    expect_true("P10: integration_key applied",
                strcmp(cfg.integration_key, "ikey-test") == 0);
    ezkey_conf_apply_key(&cfg, "secret_key", "skey-test");
    expect_true("P10: secret_key applied",
                strcmp(cfg.secret_key, "skey-test") == 0);
    ezkey_conf_apply_key(&cfg, "api_url", "https://alias.example");
    expect_true("P7/P10: api_url alias applied",
                strcmp(cfg.api_url, "https://alias.example") == 0);
    ezkey_conf_apply_key(&cfg, "api_timeout", "99");
    expect_eq("P10: api_timeout clamped to EZKEY_MAX_API_TIMEOUT",
              cfg.api_timeout, EZKEY_MAX_API_TIMEOUT);
    ezkey_conf_apply_key(&cfg, "api_timeout", "5");
    expect_eq("P10: api_timeout 5 applied", cfg.api_timeout, 5);
    cfg.lab_http = 0;
    ezkey_conf_apply_key(&cfg, "lab_http", "maybe");
    expect_eq("P10: lab_http typo keeps prior 0", cfg.lab_http, 0);

    /* P5: wipe_secrets zeros credentials. */
    ezkey_config_wipe_secrets(&cfg);
    expect_true("P5: integration_key wiped", cfg.integration_key[0] == '\0');
    expect_true("P5: secret_key wiped", cfg.secret_key[0] == '\0');
    {
        size_t i;
        int all_zero = 1;

        for (i = 0; i < sizeof(cfg.secret_key); i++) {
            if (cfg.secret_key[i] != '\0') {
                all_zero = 0;
                break;
            }
        }
        expect_true("P5: secret_key fully zeroed", all_zero);
    }

    /* P6: '#' inside value is kept; unknown key does not change lab_http. */
    {
        char path[] = "/tmp/ezkey-conf-hashval-XXXXXX";
        int fd = mkstemp(path);
        const char *body =
            "secret_key=ab#cd\n"
            "lab_http=false\n"
            "not_a_real_key=1\n"
            "# full line comment\n"
            "debug=false # trailing comment\n";

        expect_true("P6: mkstemp hash-value", fd >= 0);
        if (fd >= 0) {
            (void)write(fd, body, strlen(body));
            close(fd);
            ezkey_config_init(&cfg);
            fp = fopen(path, "r");
            if (fp != NULL) {
                expect_eq("P6: load with hash-in-value",
                          ezkey_conf_load_fp(fp, &cfg), 0);
                fclose(fp);
                expect_true("P6: secret keeps ab#cd (not truncated at #)",
                            strcmp(cfg.secret_key, "ab#cd") == 0);
                expect_eq("P6: trailing comment still works for debug",
                          cfg.debug, 0);
                expect_eq("P6: unknown key ignored (lab_http still 0)",
                          cfg.lab_http, 0);
            }
            unlink(path);
        }
    }

    printf("=== Results: %s ===\n", fails == 0 ? "all passed" : "FAILED");
    return fails == 0 ? 0 : 1;
}
