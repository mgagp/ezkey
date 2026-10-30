/*
 * ezkey_sanitize.c — neutralize untrusted strings before syslog / file logs.
 */

#include "ezkey_sanitize.h"

void ezkey_sanitize_for_log(char *dest, size_t dest_size, const char *src) {
    size_t i;
    size_t j = 0;

    if (dest == NULL || dest_size == 0) {
        return;
    }
    if (src == NULL) {
        dest[0] = '\0';
        return;
    }
    for (i = 0; src[i] != '\0' && j + 1 < dest_size; i++) {
        unsigned char c = (unsigned char)src[i];
        /* Controls, DEL, high bytes, space, '=' → underscore. */
        if (c < 0x20 || c >= 0x7f || c == ' ' || c == '=') {
            dest[j++] = '_';
        } else {
            dest[j++] = (char)c;
        }
    }
    dest[j] = '\0';
}

void ezkey_sanitize_for_prompt(char *dest, size_t dest_size, const char *src) {
    size_t i;
    size_t j = 0;

    if (dest == NULL || dest_size == 0) {
        return;
    }
    if (src == NULL) {
        dest[0] = '\0';
        return;
    }
    for (i = 0; src[i] != '\0' && j + 1 < dest_size; i++) {
        unsigned char c = (unsigned char)src[i];
        /* Controls, DEL, high bytes → underscore; keep printable ASCII. */
        if (c < 0x20 || c >= 0x7f) {
            dest[j++] = '_';
        } else {
            dest[j++] = (char)c;
        }
    }
    dest[j] = '\0';
}
