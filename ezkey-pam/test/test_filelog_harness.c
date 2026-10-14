/*
 * Harness for ezkey_filelog (#818 m2): mode clamp, hardlink refuse, FIFO non-block.
 *
 * Build/run via: make test-filelog
 * Uses EZKEY_FILELOG_TEST so ownership matches geteuid() (not only root).
 */

#define _POSIX_C_SOURCE 200809L

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include "ezkey_filelog.h"

static int failures;

static void expect(int cond, const char *name) {
    if (cond) {
        printf("[PASS] %s\n", name);
    } else {
        printf("[FAIL] %s (errno=%d %s)\n", name, errno, strerror(errno));
        failures++;
    }
}

int main(void) {
    char path[600];
    char link_path[600];
    char fifo_path[600];
    struct stat st;
    int fd;

    failures = 0;

    printf("=== ezkey_filelog harness (EZKEY_LOG_DIR=%s) ===\n", EZKEY_LOG_DIR);

    if (strncmp(EZKEY_LOG_DIR, "/tmp/", 5) != 0 &&
        strstr(EZKEY_LOG_DIR, "ezkey-filelog") == NULL) {
        fprintf(stderr, "harness refused: EZKEY_LOG_DIR looks like production path\n");
        return 2;
    }

    /* --- existing 0644 becomes 0600 --- */
    if (snprintf(path, sizeof(path), "%s/%s", EZKEY_LOG_DIR, EZKEY_LOG_OUT_NAME) >=
        (int)sizeof(path)) {
        return 2;
    }
    unlink(path);
    fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    expect(fd >= 0, "create debug.out as 0644");
    if (fd >= 0) {
        close(fd);
    }
    chmod(path, 0644);
    ezkey_filelog_append(EZKEY_LOG_OUT_NAME, "mode-clamp");
    expect(stat(path, &st) == 0 && (st.st_mode & 0777) == 0600,
           "existing 0644 file becomes 0600 after append");

    /* --- hardlink refused (nlink != 1) --- */
    if (snprintf(link_path, sizeof(link_path), "%s/debug.out.hl", EZKEY_LOG_DIR) >=
        (int)sizeof(link_path)) {
        return 2;
    }
    unlink(link_path);
    unlink(path);
    fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    expect(fd >= 0, "create debug.out for hardlink case");
    if (fd >= 0) {
        close(fd);
    }
    expect(link(path, link_path) == 0, "create hardlink to debug.out");
    {
        off_t before = 0;
        if (stat(path, &st) == 0) {
            before = st.st_size;
        }
        ezkey_filelog_append(EZKEY_LOG_OUT_NAME, "hardlink-must-not-append");
        expect(stat(path, &st) == 0 && st.st_size == before,
               "hardlink refused (size unchanged)");
    }
    unlink(link_path);
    unlink(path);

    /* --- FIFO does not block --- */
    if (snprintf(fifo_path, sizeof(fifo_path), "%s/%s", EZKEY_LOG_DIR, EZKEY_LOG_ERR_NAME) >=
        (int)sizeof(fifo_path)) {
        return 2;
    }
    unlink(fifo_path);
    expect(mkfifo(fifo_path, 0600) == 0, "create debug.err as FIFO");
    ezkey_filelog_append(EZKEY_LOG_ERR_NAME, "fifo-must-not-block");
    expect(1, "FIFO open path returned (O_NONBLOCK; no hang)");
    unlink(fifo_path);

    printf("=== Results: %s ===\n", failures == 0 ? "all passed" : "FAILED");
    return failures == 0 ? 0 : 1;
}
