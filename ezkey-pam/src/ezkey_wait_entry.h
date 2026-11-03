/*
 * ezkey_wait_entry.h — wait-entry abort contract (#1014 Patrick m1 / #1016 T1).
 *
 * Shared by pam_ezkey.c and the diag harness so the harness exercises the
 * real source, not a local copy. check-source awk remains the mutation gate
 * against clearing ezkey_wait_abort at wait entry.
 */

#ifndef EZKEY_WAIT_ENTRY_H
#define EZKEY_WAIT_ENTRY_H

#include <signal.h>

/**
 * Wait-entry contract: if abort is already raised, skip /wait.
 * Never clear the flag here — a TERM during create must still cancel.
 *
 * @param abort_flag pointer to ezkey_wait_abort (may be NULL)
 * @return non-zero when /wait must be skipped
 */
static inline int ezkey_wait_entry_abort_raised(
    const volatile sig_atomic_t *abort_flag) {
    return abort_flag != NULL && *abort_flag != 0;
}

#endif /* EZKEY_WAIT_ENTRY_H */
