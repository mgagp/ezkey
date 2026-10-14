/*
 * ezkey_filelog.h - Safe debug file logging for pam_ezkey (#818).
 *
 * Directory opened with O_DIRECTORY|O_NOFOLLOW; files via openat with
 * O_NOFOLLOW|O_CLOEXEC|O_NONBLOCK, then fstat (regular, expected uid, nlink==1)
 * and fchmod 0600.
 */

#ifndef EZKEY_FILELOG_H
#define EZKEY_FILELOG_H

#include "ezkey_config.h"

#ifndef EZKEY_LOG_DIR
#define EZKEY_LOG_DIR "/var/log/ezkey-pam"
#endif

#define EZKEY_LOG_OUT_NAME "debug.out"
#define EZKEY_LOG_ERR_NAME "debug.err"

/**
 * Append one line to EZKEY_LOG_DIR/<basename>. Fail-open (no-op) on any error.
 *
 * @param basename file name under the log dir (e.g. debug.out)
 * @param message  already-sanitized log line (no trailing newline required)
 */
void ezkey_filelog_append(const char *basename, const char *message);

#endif /* EZKEY_FILELOG_H */
