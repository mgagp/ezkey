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

    printf("=== Results: %s ===\n", fails == 0 ? "all passed" : "FAILED");
    return fails == 0 ? 0 : 1;
}
