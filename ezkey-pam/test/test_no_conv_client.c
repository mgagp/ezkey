/*
 * Lab helper: pam_authenticate with a conversation that always fails.
 * Expectation when challenge_requested=true: PAM_AUTHINFO_UNAVAIL (user must
 * see the challenge code — Christophe nit). Print pam_rc and exit 0 only if
 * UNAVAIL (9 on Linux).
 */

#include <security/pam_appl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int fail_conv(int num_msg, const struct pam_message **msg,
                     struct pam_response **resp, void *appdata_ptr) {
    (void)num_msg;
    (void)msg;
    (void)resp;
    (void)appdata_ptr;
    return PAM_CONV_ERR;
}

int main(int argc, char **argv) {
    pam_handle_t *pamh = NULL;
    struct pam_conv conv = {fail_conv, NULL};
    const char *user = "testuser";
    const char *service = "ezkey-chal-noconv";
    int rc;

    if (argc >= 2 && argv[1][0] != '\0') {
        user = argv[1];
    }
    if (argc >= 3 && argv[2][0] != '\0') {
        service = argv[2];
    }

    rc = pam_start(service, user, &conv, &pamh);
    if (rc != PAM_SUCCESS) {
        fprintf(stderr, "pam_start failed: %d\n", rc);
        return 2;
    }
    rc = pam_authenticate(pamh, 0);
    printf("pam_authenticate_rc=%d\n", rc);
    pam_end(pamh, rc);

    /* Linux PAM_AUTHINFO_UNAVAIL == 9 */
    if (rc == PAM_AUTHINFO_UNAVAIL) {
        return 0;
    }
    return 1;
}
