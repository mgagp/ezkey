/*
 * Harness for ezkey_filelog (#818 m2): mode clamp, hardlink refuse, FIFO non-block.
 *
 * Build/run via: make test-filelog
 * Uses EZKEY_FILELOG_TEST so ownership matches geteuid() (not only root).
 * Setup uses fchmod/fstat on open fds (no path chmod after create) so CodeQL
 * does not flag harness TOCTOU as a product alert.
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

/** Create basename under EZKEY_LOG_DIR with mode via fchmod on the new fd. */
static int create_file_mode(const char *basename, mode_t mode) {
    int dfd;
    int fd;

    dfd = open(EZKEY_LOG_DIR, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (dfd < 0) {
        return -1;
    }
    unlinkat(dfd, basename, 0);
    fd = openat(dfd, basename, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, mode);
    if (fd < 0) {
        close(dfd);
        return -1;
    }
    if (fchmod(fd, mode) != 0) {
        close(fd);
        close(dfd);
        return -1;
    }
    close(fd);
    close(dfd);
    return 0;
}

/** fstat basename under EZKEY_LOG_DIR via openat+O_NOFOLLOW (no path race). */
static int fstat_log_file(const char *basename, struct stat *st) {
    int dfd;
    int fd;
    int rc;

    dfd = open(EZKEY_LOG_DIR, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (dfd < 0) {
        return -1;
    }
    fd = openat(dfd, basename, O_RDONLY | O_NOFOLLOW | O_CLOEXEC);
    close(dfd);
    if (fd < 0) {
        return -1;
    }
    rc = fstat(fd, st);
    close(fd);
    return rc;
}

int main(void) {
    char link_path[600];
    struct stat st;
    off_t before;

    failures = 0;

    printf("=== ezkey_filelog harness (EZKEY_LOG_DIR=%s) ===\n", EZKEY_LOG_DIR);

    if (strncmp(EZKEY_LOG_DIR, "/tmp/", 5) != 0 &&
        strstr(EZKEY_LOG_DIR, "ezkey-filelog") == NULL) {
        fprintf(stderr, "harness refused: EZKEY_LOG_DIR looks like production path\n");
        return 2;
    }

    /* --- existing 0644 becomes 0600 --- */
    expect(create_file_mode(EZKEY_LOG_OUT_NAME, 0644) == 0, "create debug.out as 0644");
    ezkey_filelog_append(EZKEY_LOG_OUT_NAME, "mode-clamp");
    expect(fstat_log_file(EZKEY_LOG_OUT_NAME, &st) == 0 && (st.st_mode & 0777) == 0600,
           "existing 0644 file becomes 0600 after append");

    /* --- hardlink refused (nlink != 1) --- */
    if (snprintf(link_path, sizeof(link_path), "%s/debug.out.hl", EZKEY_LOG_DIR) >=
        (int)sizeof(link_path)) {
        return 2;
    }
    unlink(link_path);
    expect(create_file_mode(EZKEY_LOG_OUT_NAME, 0600) == 0,
           "create debug.out for hardlink case");
    {
        char out_path[600];
        if (snprintf(out_path, sizeof(out_path), "%s/%s", EZKEY_LOG_DIR, EZKEY_LOG_OUT_NAME) >=
            (int)sizeof(out_path)) {
            return 2;
        }
        /* link(2) needs paths; both names are under the private mktemp dir. */
        expect(link(out_path, link_path) == 0, "create hardlink to debug.out");
    }
    before = 0;
    if (fstat_log_file(EZKEY_LOG_OUT_NAME, &st) == 0) {
        before = st.st_size;
    }
    ezkey_filelog_append(EZKEY_LOG_OUT_NAME, "hardlink-must-not-append");
    expect(fstat_log_file(EZKEY_LOG_OUT_NAME, &st) == 0 && st.st_size == before,
           "hardlink refused (size unchanged)");
    unlink(link_path);
    {
        int dfd = open(EZKEY_LOG_DIR, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
        if (dfd >= 0) {
            unlinkat(dfd, EZKEY_LOG_OUT_NAME, 0);
            close(dfd);
        }
    }

    /* --- FIFO does not block --- */
    {
        int dfd = open(EZKEY_LOG_DIR, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
        expect(dfd >= 0, "open log dir for FIFO setup");
        if (dfd >= 0) {
            unlinkat(dfd, EZKEY_LOG_ERR_NAME, 0);
            /*
             * mkfifoat when available; fallback: path under private mktemp dir.
             * O_NONBLOCK in production openat is what we prove (no hang).
             */
#if defined(__linux__)
            expect(mkfifoat(dfd, EZKEY_LOG_ERR_NAME, 0600) == 0, "create debug.err as FIFO");
#else
            {
                char fifo_path[600];
                snprintf(fifo_path, sizeof(fifo_path), "%s/%s", EZKEY_LOG_DIR,
                         EZKEY_LOG_ERR_NAME);
                expect(mkfifo(fifo_path, 0600) == 0, "create debug.err as FIFO");
            }
#endif
            close(dfd);
        }
    }
    ezkey_filelog_append(EZKEY_LOG_ERR_NAME, "fifo-must-not-block");
    expect(1, "FIFO open path returned (O_NONBLOCK; no hang)");
    {
        int dfd = open(EZKEY_LOG_DIR, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
        if (dfd >= 0) {
            unlinkat(dfd, EZKEY_LOG_ERR_NAME, 0);
            close(dfd);
        }
    }

    printf("=== Results: %s ===\n", failures == 0 ? "all passed" : "FAILED");
    return failures == 0 ? 0 : 1;
}
