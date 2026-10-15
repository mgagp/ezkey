/*
 * ezkey_filelog.c - TOCTOU-hardened debug file append for pam_ezkey (#818).
 */

#define _POSIX_C_SOURCE 200809L

#include "ezkey_filelog.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#ifdef EZKEY_FILELOG_TEST
#define EZKEY_FILELOG_EXPECTED_UID ((uid_t)geteuid())
#else
#define EZKEY_FILELOG_EXPECTED_UID ((uid_t)0)
#endif

static int ezkey_open_log_dir(void) {
    struct stat st;
    int dfd;

    if (mkdir(EZKEY_LOG_DIR, 0700) != 0 && errno != EEXIST) {
        return -1;
    }

    dfd = open(EZKEY_LOG_DIR, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (dfd < 0) {
        return -1;
    }
    if (fstat(dfd, &st) != 0) {
        close(dfd);
        return -1;
    }
    if (!S_ISDIR(st.st_mode) || st.st_uid != EZKEY_FILELOG_EXPECTED_UID ||
        (st.st_mode & 077) != 0) {
        close(dfd);
        return -1;
    }
    return dfd;
}

static int ezkey_open_log_file(int dfd, const char *basename) {
    struct stat st;
    int fd;

    if (basename == NULL || strchr(basename, '/') != NULL) {
        return -1;
    }

    fd = openat(dfd, basename,
                O_WRONLY | O_CREAT | O_APPEND | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK,
                0600);
    if (fd < 0) {
        return -1;
    }
    if (fstat(fd, &st) != 0) {
        close(fd);
        return -1;
    }
    if (!S_ISREG(st.st_mode) || st.st_uid != EZKEY_FILELOG_EXPECTED_UID ||
        st.st_nlink != 1) {
        close(fd);
        return -1;
    }
    if (fchmod(fd, 0600) != 0) {
        close(fd);
        return -1;
    }
    return fd;
}

void ezkey_filelog_append(const char *basename, const char *message) {
    int dfd;
    int fd;
    FILE *fp;

    if (message == NULL || basename == NULL) {
        return;
    }

    dfd = ezkey_open_log_dir();
    if (dfd < 0) {
        return;
    }
    fd = ezkey_open_log_file(dfd, basename);
    close(dfd);
    if (fd < 0) {
        return;
    }

    fp = fdopen(fd, "a");
    if (fp == NULL) {
        close(fd);
        return;
    }
    fprintf(fp, "%s\n", message);
    fclose(fp);
}
