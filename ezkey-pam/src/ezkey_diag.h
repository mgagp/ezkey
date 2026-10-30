/*
 * ezkey_diag.h — authpriv attempt diagnostics (#1011).
 *
 * Richer UNAVAIL (and related) attempt fields, plus a best-effort final
 * attempt line when sshd kills the PAM child during a blocking prompt.
 * Signal-handler emit uses only async-signal-safe calls (write to a
 * pre-opened /dev/log FD). Never logs secrets, tokens, bodies, or IPs.
 */

#ifndef EZKEY_DIAG_H
#define EZKEY_DIAG_H

#include <signal.h>
#include <stddef.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

/** Max length of one authpriv attempt line (incl. syslog PRI prefix). */
#define EZKEY_DIAG_LINE_MAX 768

/**
 * Mutable diagnostics for one pam_sm_authenticate call.
 *
 * stage/reason are fixed vocabularies (see ezkey_diag.c). Numeric fields use
 * -1 for “unknown / not applicable” and are rendered as `-` on the line.
 */
typedef struct {
    char user[128];
    long auth_attempt_id; /* <=0 → "-" */
    const char *result;
    const char *cancel_status;
    int pam_rc;
    long latency_ms;
    int lab_http;
    int timeout_seconds; /* <=0 → "-" */
    const char *stage;
    const char *reason;
    int curl_rc;     /* -1 → "-" */
    long http;       /* -1 → "-" */
    long confirm_ms; /* -1 → "-" */
    long api_ms;     /* -1 → "-" */
} EzkeyDiag;

/**
 * Initialize diag to safe defaults (UNAVAIL / skipped / stage=init).
 *
 * @param diag diagnostics block (must not be NULL)
 * @param username Linux username (may be NULL)
 * @param lab_http conf lab_http flag
 */
void ezkey_diag_init(EzkeyDiag *diag, const char *username, int lab_http);

/**
 * Format the canonical authpriv attempt line (no syslog PRI prefix).
 *
 * @param dest output buffer
 * @param dest_size capacity of dest
 * @param diag source fields
 * @return bytes written excluding NUL, or -1 on error
 */
int ezkey_diag_format_attempt(char *dest, size_t dest_size,
                              const EzkeyDiag *diag);

/**
 * Format a /dev/log datagram: `<PRI>attempt-line`.
 *
 * @param dest output buffer
 * @param dest_size capacity
 * @param diag source fields
 * @param out_len receives byte count to write (excludes NUL)
 * @return 0 on success, -1 on error
 */
int ezkey_diag_format_syslog(char *dest, size_t dest_size, const EzkeyDiag *diag,
                             size_t *out_len);

/**
 * Double-buffered SIGNAL syslog snapshot (#1016 N5).
 *
 * Publish writes the inactive slot to completion, then flips {@code slot}.
 * A SIGTERM mid-refresh still observes the previous complete snapshot
 * (never a zeroed length on the active slot).
 */
typedef struct {
    char buf[2][EZKEY_DIAG_LINE_MAX];
    /** Published byte counts; read only after observing {@code slot}. */
    volatile size_t len[2];
    /** Active slot: 0 or 1. */
    volatile sig_atomic_t slot;
} EzkeyDiagSyslogSnap;

/**
 * Zero both slots (call at authenticate entry).
 *
 * @param snap snapshot pair (must not be NULL)
 */
void ezkey_diag_syslog_snap_clear(EzkeyDiagSyslogSnap *snap);

/**
 * Format {@code diag} into the inactive slot, then publish by flipping slot.
 *
 * @param snap snapshot pair
 * @param diag source fields
 * @return 0 on success, -1 on format error (active slot unchanged)
 */
int ezkey_diag_syslog_snap_publish(EzkeyDiagSyslogSnap *snap,
                                   const EzkeyDiag *diag);

/**
 * Read the currently published snapshot (async-signal-safe).
 *
 * @param snap snapshot pair
 * @param msg_out receives pointer to published bytes (never NULL if snap ok)
 * @param len_out receives published length
 */
void ezkey_diag_syslog_snap_read(const EzkeyDiagSyslogSnap *snap,
                                 const char **msg_out, size_t *len_out);

/**
 * Open /dev/log for signal-safe emit. Fail-open (returns -1).
 * Prefers SOCK_DGRAM|SOCK_CLOEXEC|SOCK_NONBLOCK; falls back to socket()
 * plus fcntl(O_NONBLOCK) / FD_CLOEXEC when those flags are absent (#1016 P-b).
 * A full queue yields EAGAIN instead of blocking the SIGTERM handler.
 *
 * @return connected non-blocking SOCK_DGRAM fd, or -1
 */
int ezkey_diag_open_log_fd(void);

/**
 * Close a fd from ezkey_diag_open_log_fd (no-op if fd < 0).
 *
 * @param fd log socket
 */
void ezkey_diag_close_log_fd(int fd);

/**
 * Async-signal-safe: write preformatted syslog bytes once.
 *
 * @param fd /dev/log fd (or -1)
 * @param msg preformatted datagram
 * @param len byte count
 * @param emitted_flag set to 1 after this call claims the emit gate
 * @return 1 if this call claimed the gate (and attempted write), 0 if already
 *         emitted
 */
int ezkey_diag_emit_signal_safe(int fd, const char *msg, size_t len,
                                volatile sig_atomic_t *emitted_flag);

#ifdef __cplusplus
}
#endif

#endif /* EZKEY_DIAG_H */
