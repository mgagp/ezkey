/*
 * ezkey_sanitize.h — neutralize untrusted strings before syslog / file logs.
 */

#ifndef EZKEY_SANITIZE_H
#define EZKEY_SANITIZE_H

#include <stddef.h>

/**
 * Neutralize control characters, high bytes (>= 0x80), spaces, and '=' before
 * syslog / file logs (#818 m3 + M1 review). Spaces/'=' would let a crafted
 * username forge attempt-line fields; non-ASCII bytes stay out of the parse
 * surface. Writes into dest (NUL-terminated); always succeeds for non-NULL dest.
 *
 * @param dest destination buffer
 * @param dest_size capacity of dest
 * @param src source string (may be NULL → empty dest)
 */
void ezkey_sanitize_for_log(char *dest, size_t dest_size, const char *src);

#endif /* EZKEY_SANITIZE_H */
