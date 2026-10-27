# Makefile for pam_ezkey
# Build inside the module Docker image (Rocky or AL2023). Host builds also work
# when gcc, pam, libcurl, and cJSON development packages are installed.

CC = gcc
# #1016 C6: stack protector + fortified libc + full RELRO for a root PAM module.
# _FORTIFY_SOURCE requires -O1 or higher (we use -O2).
CFLAGS = -fPIC -Wall -Wextra -O2 -std=c99 -D_POSIX_C_SOURCE=200809L \
	-fstack-protector-strong -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=2
# #1016 S4: keep -l* in LDLIBS AFTER objects so --as-needed linkers still
# record libcurl / libcjson in DT_NEEDED (required with -z now + dlopen).
LDFLAGS = -shared -Wl,-z,relro,-z,now
LDLIBS = -lpam -lcurl -lcjson -lm
MODULE_NAME = pam_ezkey.so
SRC_DIR = src
BUILD_DIR = build
TEST_DIR = test
INSTALL_DIR = /lib64/security

OBJECTS = $(BUILD_DIR)/pam_ezkey.o $(BUILD_DIR)/ezkey_filelog.o \
	$(BUILD_DIR)/ezkey_sanitize.o $(BUILD_DIR)/ezkey_challenge.o \
	$(BUILD_DIR)/ezkey_budget.o $(BUILD_DIR)/ezkey_session.o \
	$(BUILD_DIR)/ezkey_conf.o $(BUILD_DIR)/ezkey_diag.o \
	$(BUILD_DIR)/ezkey_http.o

.PHONY: all debug install uninstall clean check-pam check-source check-hardening \
	lint help test test-filelog test-sanitize test-challenge test-conf \
	test-budget test-session test-diag test-http test-decision \
	test-install-sshd-warn

all: $(BUILD_DIR)/$(MODULE_NAME)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/pam_ezkey.o: $(SRC_DIR)/pam_ezkey.c $(SRC_DIR)/ezkey_config.h \
		$(SRC_DIR)/ezkey_conf.h $(SRC_DIR)/ezkey_filelog.h \
		$(SRC_DIR)/ezkey_sanitize.h $(SRC_DIR)/ezkey_challenge.h \
		$(SRC_DIR)/ezkey_budget.h $(SRC_DIR)/ezkey_session.h \
		$(SRC_DIR)/ezkey_diag.h $(SRC_DIR)/ezkey_http.h \
		$(SRC_DIR)/ezkey_wait_entry.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/pam_ezkey.c -o $(BUILD_DIR)/pam_ezkey.o

$(BUILD_DIR)/ezkey_diag.o: $(SRC_DIR)/ezkey_diag.c $(SRC_DIR)/ezkey_diag.h \
		$(SRC_DIR)/ezkey_config.h $(SRC_DIR)/ezkey_sanitize.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/ezkey_diag.c -o $(BUILD_DIR)/ezkey_diag.o

$(BUILD_DIR)/ezkey_http.o: $(SRC_DIR)/ezkey_http.c $(SRC_DIR)/ezkey_http.h \
		$(SRC_DIR)/ezkey_sanitize.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/ezkey_http.c -o $(BUILD_DIR)/ezkey_http.o

$(BUILD_DIR)/ezkey_conf.o: $(SRC_DIR)/ezkey_conf.c $(SRC_DIR)/ezkey_conf.h \
		$(SRC_DIR)/ezkey_config.h $(SRC_DIR)/ezkey_budget.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/ezkey_conf.c -o $(BUILD_DIR)/ezkey_conf.o

$(BUILD_DIR)/ezkey_filelog.o: $(SRC_DIR)/ezkey_filelog.c $(SRC_DIR)/ezkey_filelog.h \
		$(SRC_DIR)/ezkey_config.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/ezkey_filelog.c -o $(BUILD_DIR)/ezkey_filelog.o

$(BUILD_DIR)/ezkey_sanitize.o: $(SRC_DIR)/ezkey_sanitize.c $(SRC_DIR)/ezkey_sanitize.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/ezkey_sanitize.c -o $(BUILD_DIR)/ezkey_sanitize.o

$(BUILD_DIR)/ezkey_challenge.o: $(SRC_DIR)/ezkey_challenge.c $(SRC_DIR)/ezkey_challenge.h \
		| $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/ezkey_challenge.c -o $(BUILD_DIR)/ezkey_challenge.o

$(BUILD_DIR)/ezkey_budget.o: $(SRC_DIR)/ezkey_budget.c $(SRC_DIR)/ezkey_budget.h \
		$(SRC_DIR)/ezkey_config.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/ezkey_budget.c -o $(BUILD_DIR)/ezkey_budget.o

$(BUILD_DIR)/ezkey_session.o: $(SRC_DIR)/ezkey_session.c $(SRC_DIR)/ezkey_session.h \
		$(SRC_DIR)/ezkey_config.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/ezkey_session.c -o $(BUILD_DIR)/ezkey_session.o

$(BUILD_DIR)/$(MODULE_NAME): $(OBJECTS)
	$(CC) $(LDFLAGS) -o $(BUILD_DIR)/$(MODULE_NAME) $(OBJECTS) $(LDLIBS)

debug: CFLAGS += -DDEBUG -g
debug: all

install: $(BUILD_DIR)/$(MODULE_NAME)
	@echo "Installing PAM module to $(INSTALL_DIR)..."
	install -d $(INSTALL_DIR)
	install -m 755 $(BUILD_DIR)/$(MODULE_NAME) $(INSTALL_DIR)/$(MODULE_NAME)
	install -d /etc/security
	install -m 600 config/pam_ezkey.conf /etc/security/pam_ezkey.conf
	install -d -m 700 /var/log/ezkey-pam
	@echo "Module installed. Edit /etc/security/pam_ezkey.conf before use."

uninstall:
	rm -f $(INSTALL_DIR)/$(MODULE_NAME)

clean:
	rm -rf $(BUILD_DIR)

check-pam:
	@if grep -q "pam_ezkey" /etc/pam.d/sshd; then \
		echo "pam_ezkey found in SSH PAM configuration"; \
	else \
		echo "pam_ezkey NOT found in SSH PAM configuration"; \
	fi

lint:
	cppcheck --enable=all --std=c99 $(SRC_DIR)/

# Source guard (#818). Host needs gcc, pam, libcurl, cjson devel packages.
check-source:
	@if grep -nE '/tmp/pam_ezkey' $(SRC_DIR)/*.c $(SRC_DIR)/*.h; then \
		echo "error: /tmp/pam_ezkey path still present (#818)" >&2; exit 1; \
	fi
	@grep -q 'O_NOFOLLOW' $(SRC_DIR)/ezkey_filelog.c
	@grep -q 'openat' $(SRC_DIR)/ezkey_filelog.c
	@grep -q 'O_NONBLOCK' $(SRC_DIR)/ezkey_filelog.c
	@grep -q 'ezkey_sanitize_for_log' $(SRC_DIR)/ezkey_sanitize.c
	@grep -q 'ezkey_sanitize_for_prompt' $(SRC_DIR)/ezkey_sanitize.c
	@grep -q 'c >= 0x7f' $(SRC_DIR)/ezkey_sanitize.c
	@grep -q '/var/log/ezkey-pam' $(SRC_DIR)/ezkey_filelog.h
	@grep -q 'ezkey_format_waiting_prompt' $(SRC_DIR)/pam_ezkey.c
	@grep -q 'ezkey_challenge_display_ok' $(SRC_DIR)/pam_ezkey.c
	@grep -q 'ezkey_prewait_prompt_mode' $(SRC_DIR)/pam_ezkey.c
	@grep -q 'ezkey_prewait_response_confirmed' $(SRC_DIR)/pam_ezkey.c
	@grep -q 'EZKEY_DEFAULT_CHALLENGE_REQUESTED' $(SRC_DIR)/ezkey_config.h
	# Patrick #995 B1: assignment only — a comment must not satisfy this guard.
	@grep -qE 'cfg->challenge_requested[[:space:]]*=[[:space:]]*EZKEY_DEFAULT_CHALLENGE_REQUESTED' \
		$(SRC_DIR)/ezkey_conf.c
	@grep -qE '^challenge_requested=true$$' config/pam_ezkey.conf
	@grep -qE '^challenge_requested=true$$' docker-entrypoint.sh
	# #1011: sample conf comment must say default true (not the pre-#995 false).
	@grep -qE 'challenge_requested[[:space:]].*default true' config/pam_ezkey.conf
	@if grep -nE 'challenge_requested[[:space:]].*default false' config/pam_ezkey.conf; then \
		echo "error: challenge_requested comment still says default false (#1011)" >&2; exit 1; \
	fi
	# #1011 B3: format/call sites must be real statements (comment-only fails).
	# cancel=%s sits mid-string (not at a quote boundary) after the #1011 field split.
	@grep -qE 'cancel=%s timeoutSeconds=%s' $(SRC_DIR)/ezkey_diag.c
	@grep -qE '"stage=%s reason=%s' $(SRC_DIR)/ezkey_diag.c
	@grep -qE 'LOG_AUTHPRIV[[:space:]]*\|[[:space:]]*LOG_INFO' $(SRC_DIR)/ezkey_diag.c
	@grep -qE 'SOCK_DGRAM[[:space:]]*\|[[:space:]]*SOCK_CLOEXEC[[:space:]]*\|[[:space:]]*SOCK_NONBLOCK' \
		$(SRC_DIR)/ezkey_diag.c
	# #1016 P-b: fcntl O_NONBLOCK fallback must remain (portable path).
	@grep -qE 'fcntl[[:space:]]*\([^,]+,[[:space:]]*F_SETFL[[:space:]]*,.*O_NONBLOCK' \
		$(SRC_DIR)/ezkey_diag.c || \
		(echo "error: ezkey_diag_open_log_fd must fcntl O_NONBLOCK (#1016 P-b)" >&2; exit 1)
	# #1016 N5: double-buffer publish (never zero active SIGNAL length mid-refresh).
	@grep -qE 'ezkey_diag_syslog_snap_publish' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: SIGNAL refresh must use ezkey_diag_syslog_snap_publish (#1016 N5)" >&2; exit 1)
	@grep -qE 'ezkey_diag_syslog_snap_read' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: SIGTERM handler must ezkey_diag_syslog_snap_read (#1016 N5)" >&2; exit 1)
	# Patrick m2: statement-form "(void)ezkey_diag_emit_signal_safe(" only —
	# a comment that merely names the symbol (or `* ezkey_diag_emit…`) must fail.
	@grep -qE '^[[:space:]]*\(void\)ezkey_diag_emit_signal_safe[[:space:]]*\(' \
		$(SRC_DIR)/pam_ezkey.c || \
		(echo "error: live (void)ezkey_diag_emit_signal_safe( call missing (Patrick m2)" >&2; exit 1)
	@grep -qE 'ezkey_in_wait[[:space:]]*!=' $(SRC_DIR)/pam_ezkey.c
	@grep -qE 'raise[[:space:]]*\([[:space:]]*SIGTERM[[:space:]]*\)' $(SRC_DIR)/pam_ezkey.c
	@grep -qE 'saved_errno' $(SRC_DIR)/pam_ezkey.c
	# Patrick m1 / #1016 T1: wait entry uses shared helper; never clear abort.
	# End pattern uses trailing ';' so the comment "ezkey_in_wait=1" does not end the range.
	@awk '/Chunked \/wait/,/^[[:space:]]*ezkey_in_wait[[:space:]]*=[[:space:]]*1[[:space:]]*;/' \
		$(SRC_DIR)/pam_ezkey.c | \
		grep -qE 'ezkey_wait_entry_abort_raised[[:space:]]*\(' || \
		(echo "error: wait entry must call ezkey_wait_entry_abort_raised before ezkey_in_wait=1" >&2; exit 1)
	@grep -qE 'abort_flag[[:space:]]*!=[[:space:]]*0' $(SRC_DIR)/ezkey_wait_entry.h || \
		(echo "error: ezkey_wait_entry.h must observe abort_flag != 0 (#1016 T1)" >&2; exit 1)
	@if awk '/Chunked \/wait/,/^[[:space:]]*ezkey_in_wait[[:space:]]*=[[:space:]]*1[[:space:]]*;/' \
		$(SRC_DIR)/pam_ezkey.c | \
		grep -nE 'ezkey_wait_abort[[:space:]]*=[[:space:]]*0'; then \
		echo "error: must not clear ezkey_wait_abort at wait entry (Patrick m1)" >&2; exit 1; \
	fi
	# Patrick B2 (#982): match the real pam_prompt call — comments alone must not pass.
	@grep -qE 'pam_prompt[[:space:]]*\([^)]*PAM_PROMPT_ECHO_ON' $(SRC_DIR)/pam_ezkey.c
	@if grep -nE 'pam_prompt[[:space:]]*\([^)]*PAM_PROMPT_ECHO_OFF' $(SRC_DIR)/pam_ezkey.c; then \
		echo "error: pam_prompt(..., PAM_PROMPT_ECHO_OFF) must not remain (#982 tier 0)" >&2; exit 1; \
	fi
	@if grep -nE '(^|[^_[:alnum:]])pam_info[[:space:]]*\(' $(SRC_DIR)/pam_ezkey.c; then \
		echo "error: pam_info() must not appear in pam_ezkey.c (#941 — use pam_prompt)" >&2; exit 1; \
	fi
	# #1016 P-a: test-diag must keep an external timeout watchdog.
	@grep -qE '^[[:space:]]*timeout[[:space:]].*test_diag_harness' $(MAKEFILE_LIST) || \
		grep -qE 'timeout --foreground.*test_diag_harness' $(MAKEFILE_LIST) || \
		(echo "error: test-diag must run under timeout (#1016 P-a)" >&2; exit 1)
	# #1016 C1/C2: body cap + empty PROXY + NETRC ignored (statement form).
	@grep -qE 'EZKEY_HTTP_BODY_MAX' $(SRC_DIR)/ezkey_http.h || \
		(echo "error: EZKEY_HTTP_BODY_MAX missing (#1016 C1)" >&2; exit 1)
	@grep -qE 'CURLOPT_MAXFILESIZE' $(SRC_DIR)/ezkey_http.c || \
		(echo "error: CURLOPT_MAXFILESIZE missing (#1016 C1)" >&2; exit 1)
	@grep -qE 'CURLOPT_PROXY[[:space:]]*,[[:space:]]*""' $(SRC_DIR)/ezkey_http.c || \
		(echo "error: CURLOPT_PROXY \"\" missing (#1016 C2)" >&2; exit 1)
	@grep -qE 'CURL_NETRC_IGNORED' $(SRC_DIR)/ezkey_http.c || \
		(echo "error: CURLOPT_NETRC IGNORED missing (#1016 extra)" >&2; exit 1)
	@grep -qE 'ezkey_http_apply_transport_guards' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: pam_ezkey must call ezkey_http_apply_transport_guards" >&2; exit 1)
	@grep -qE 'ezkey_json_double_to_long' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: authAttemptId/challenge must use ezkey_json_double_to_long (#1016 C4)" >&2; exit 1)
	@grep -qE 'ezkey_url_for_log' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: debug api_url must use ezkey_url_for_log (#1016 C8)" >&2; exit 1)
	@grep -qE 'rc = -3|return -3' $(SRC_DIR)/ezkey_conf.c || \
		(echo "error: overlong conf line must return -3 (#1016 C3)" >&2; exit 1)
	@grep -qE 'ezkey_conf_wipe_line|explicit_bzero' $(SRC_DIR)/ezkey_conf.c || \
		(echo "error: conf line buffer must be wiped (#1016 S2)" >&2; exit 1)
	@grep -qE 'ezkey_env_sslkeylogfile_set' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: pam_ezkey must refuse SSLKEYLOGFILE (#1016 S1)" >&2; exit 1)
	@grep -qE 'reason = "env"' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: SSLKEYLOGFILE refuse must set reason=env (#1016 S1)" >&2; exit 1)
	# #1016 P1/P3/P4/P5 decision + TLS + lab_http + wipe guards.
	@grep -qE 'ezkey_wait_status_decide' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: wait path must call ezkey_wait_status_decide (#1016 P1)" >&2; exit 1)
	@grep -qE 'strcmp[[:space:]]*\([^)]*"ACCEPTED"' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: ACCEPTED strcmp must remain visible (#1016 P1)" >&2; exit 1)
	@grep -qE 'ezkey_pam_rc_for_http|ezkey_api_url_allowed_url' $(SRC_DIR)/ezkey_decision.h || \
		(echo "error: ezkey_decision.h missing HTTP/URL helpers (#1016 P1)" >&2; exit 1)
	@grep -qE 'S_ISREG' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: conf load must check S_ISREG (#1016 P1)" >&2; exit 1)
	@grep -qE 'st_uid[[:space:]]*!=[[:space:]]*0' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: conf load must check root uid (#1016 P1)" >&2; exit 1)
	@grep -qE 'mode[[:space:]]*!=[[:space:]]*0600' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: conf load must require 0600/0400 (#1016 P1)" >&2; exit 1)
	@grep -qE 'CURLOPT_TIMEOUT[[:space:]]*,[[:space:]]*timeout_seconds' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: CURLOPT_TIMEOUT must use timeout_seconds (#1016 P1)" >&2; exit 1)
	@grep -qE 'do_cancel[[:space:]]*=[[:space:]]*1' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: do_cancel=1 after create required (#1016 P1)" >&2; exit 1)
	@grep -qE 'ezkey_prewait_response_confirmed' $(SRC_DIR)/pam_ezkey.c || \
		(echo "error: confirm ok step must remain (#1016 P1)" >&2; exit 1)
	@grep -qE 'CURLOPT_SSL_VERIFYPEER[[:space:]]*,[[:space:]]*1L' $(SRC_DIR)/ezkey_http.c || \
		(echo "error: CURLOPT_SSL_VERIFYPEER 1L required (#1016 P3)" >&2; exit 1)
	@grep -qE 'CURLOPT_SSL_VERIFYHOST[[:space:]]*,[[:space:]]*2L' $(SRC_DIR)/ezkey_http.c || \
		(echo "error: CURLOPT_SSL_VERIFYHOST 2L required (#1016 P3)" >&2; exit 1)
	@if grep -nE 'CURLOPT_SSL_VERIFYPEER[[:space:]]*,[[:space:]]*0' $(SRC_DIR)/*.c; then \
		echo "error: CURLOPT_SSL_VERIFYPEER 0 forbidden (#1016 P3)" >&2; exit 1; \
	fi
	@if grep -nE 'CURLOPT_SSL_VERIFYHOST[[:space:]]*,[[:space:]]*0' $(SRC_DIR)/*.c; then \
		echo "error: CURLOPT_SSL_VERIFYHOST 0 forbidden (#1016 P3)" >&2; exit 1; \
	fi
	@grep -qE 'cfg->lab_http[[:space:]]*=[[:space:]]*0' $(SRC_DIR)/ezkey_conf.c || \
		(echo "error: lab_http default must assign 0 (#1016 P4)" >&2; exit 1)
	@grep -qE 'ezkey_config_wipe_secrets|explicit_bzero' $(SRC_DIR)/ezkey_conf.c || \
		(echo "error: wipe secrets / explicit_bzero missing (#1016 P5)" >&2; exit 1)
	@grep -qE 'conf_unknown_key' $(SRC_DIR)/ezkey_conf.c || \
		(echo "error: unknown conf key must warn (#1016 P6)" >&2; exit 1)
	# #1016 C6/S4: hardening flags + LDLIBS after objects.
	@grep -qE 'fstack-protector-strong' $(MAKEFILE_LIST) || \
		(echo "error: -fstack-protector-strong missing (#1016 C6)" >&2; exit 1)
	@grep -qE '_FORTIFY_SOURCE=2' $(MAKEFILE_LIST) || \
		(echo "error: -D_FORTIFY_SOURCE=2 missing (#1016 C6)" >&2; exit 1)
	@grep -qE '\-z,relro,\-z,now|\-z relro.*\-z now' $(MAKEFILE_LIST) || \
		(echo "error: -Wl,-z,relro,-z,now missing (#1016 C6)" >&2; exit 1)
	@grep -qE '^LDLIBS[[:space:]]*=' $(MAKEFILE_LIST) || \
		(echo "error: LDLIBS must hold -l libs after objects (#1016 S4)" >&2; exit 1)
	@grep -qF '$$(OBJECTS) $$(LDLIBS)' Makefile || \
		(echo "error: link line must be OBJECTS then LDLIBS (#1016 S4)" >&2; exit 1)
	@echo "check-source: openat/O_NOFOLLOW/O_NONBLOCK + sanitize + #941/#975/#982/#995/#1016 guards"

# #1016 C6/S4/S5: BIND_NOW, RELRO, stack protector, FORTIFY _chk, DT_NEEDED.
check-hardening: $(BUILD_DIR)/$(MODULE_NAME)
	@command -v readelf >/dev/null 2>&1 || \
		(echo "error: readelf required for check-hardening (#1016 C6)" >&2; exit 1)
	@readelf -d $(BUILD_DIR)/$(MODULE_NAME) | grep -q 'BIND_NOW' || \
		(echo "error: pam_ezkey.so missing BIND_NOW (old LDFLAGS without -z now)" >&2; exit 1)
	@readelf -l $(BUILD_DIR)/$(MODULE_NAME) | grep -q 'GNU_RELRO' || \
		(echo "error: pam_ezkey.so missing GNU_RELRO (need -z relro)" >&2; exit 1)
	@readelf -s $(BUILD_DIR)/$(MODULE_NAME) | grep -qE '__stack_chk_(fail|guard)' || \
		(echo "error: pam_ezkey.so missing stack protector symbol (old CFLAGS)" >&2; exit 1)
	# Require a FORTIFY libc _chk other than stack_chk (stack-protector alone is not enough).
	@readelf -s $(BUILD_DIR)/$(MODULE_NAME) | grep -E '_chk(@|$$)' | grep -qvE 'stack_chk' || \
		(echo "error: pam_ezkey.so missing FORTIFY _chk symbols (need -D_FORTIFY_SOURCE=2)" >&2; exit 1)
	@readelf -d $(BUILD_DIR)/$(MODULE_NAME) | grep NEEDED | grep -q 'libcurl\.so' || \
		(echo "error: pam_ezkey.so DT_NEEDED missing libcurl (LDLIBS order / --as-needed)" >&2; exit 1)
	@readelf -d $(BUILD_DIR)/$(MODULE_NAME) | grep NEEDED | grep -q 'libcjson\.so' || \
		(echo "error: pam_ezkey.so DT_NEEDED missing libcjson (LDLIBS order / --as-needed)" >&2; exit 1)
	@echo "check-hardening: BIND_NOW + GNU_RELRO + stack/_chk + DT_NEEDED curl/cjson OK"

# Harness: 0644→0600, hardlink refused, FIFO non-blocking (#818 m2).
test-filelog: $(SRC_DIR)/ezkey_filelog.c $(TEST_DIR)/test_filelog_harness.c | $(BUILD_DIR)
	@$(TEST_DIR)/run-filelog-harness.sh "$(CC)" "$(CFLAGS)" "$(SRC_DIR)" "$(BUILD_DIR)"

# Harness: sanitize space/=/controls/high-bytes (M1 review).
test-sanitize: $(BUILD_DIR)/ezkey_sanitize.o $(TEST_DIR)/test_sanitize_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_sanitize_harness.c \
		$(BUILD_DIR)/ezkey_sanitize.o -o $(BUILD_DIR)/test_sanitize_harness
	$(BUILD_DIR)/test_sanitize_harness

# Harness: challenge 0..99 + display_ok + waiting prompt sanitize (Christophe / #941).
test-challenge: $(BUILD_DIR)/ezkey_challenge.o $(BUILD_DIR)/ezkey_sanitize.o \
		$(TEST_DIR)/test_challenge_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_challenge_harness.c \
		$(BUILD_DIR)/ezkey_challenge.o $(BUILD_DIR)/ezkey_sanitize.o \
		-lpam -o $(BUILD_DIR)/test_challenge_harness
	$(BUILD_DIR)/test_challenge_harness

# Harness: conf missing challenge_requested key + bool typo fallback (#995 B1/m1).
test-conf: $(BUILD_DIR)/ezkey_conf.o $(BUILD_DIR)/ezkey_budget.o \
		$(TEST_DIR)/test_conf_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_conf_harness.c \
		$(BUILD_DIR)/ezkey_conf.o $(BUILD_DIR)/ezkey_budget.o \
		-o $(BUILD_DIR)/test_conf_harness
	$(BUILD_DIR)/test_conf_harness

# Harness: AUTH_BUDGET 105 + rem-too-small → −1 (Patrick #897 CHANGES).
test-budget: $(BUILD_DIR)/ezkey_budget.o $(TEST_DIR)/test_budget_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_budget_harness.c \
		$(BUILD_DIR)/ezkey_budget.o -o $(BUILD_DIR)/test_budget_harness
	$(BUILD_DIR)/test_budget_harness

# Harness: final-verdict block + interrupt cancel timeout (#907 F9).
test-session: $(BUILD_DIR)/ezkey_session.o $(TEST_DIR)/test_session_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_session_harness.c \
		$(BUILD_DIR)/ezkey_session.o -o $(BUILD_DIR)/test_session_harness
	$(BUILD_DIR)/test_session_harness

# Harness: attempt-line fields + signal-safe emit gate (#1011).
# #1016 P-a: external timeout so a blocking /dev/log write cannot hang CI.
TEST_DIAG_TIMEOUT_SECS ?= 15
test-diag: $(BUILD_DIR)/ezkey_diag.o $(BUILD_DIR)/ezkey_sanitize.o \
		$(TEST_DIR)/test_diag_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_diag_harness.c \
		$(BUILD_DIR)/ezkey_diag.o $(BUILD_DIR)/ezkey_sanitize.o \
		-o $(BUILD_DIR)/test_diag_harness
	@command -v timeout >/dev/null 2>&1 || \
		(echo "error: coreutils timeout required for test-diag watchdog (#1016 P-a)" >&2; exit 1)
	timeout --foreground $(TEST_DIAG_TIMEOUT_SECS)s $(BUILD_DIR)/test_diag_harness

# Harness: body cap / PROXY "" / json long / URL redact (#1016 C1/C2/C4/C8).
TEST_HTTP_TIMEOUT_SECS ?= 30
test-http: $(BUILD_DIR)/ezkey_http.o $(BUILD_DIR)/ezkey_sanitize.o \
		$(TEST_DIR)/test_http_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_http_harness.c \
		$(BUILD_DIR)/ezkey_http.o $(BUILD_DIR)/ezkey_sanitize.o \
		-lcurl -lm -o $(BUILD_DIR)/test_http_harness
	@command -v timeout >/dev/null 2>&1 || \
		(echo "error: timeout required for test-http" >&2; exit 1)
	timeout --foreground $(TEST_HTTP_TIMEOUT_SECS)s $(BUILD_DIR)/test_http_harness

# Harness: wait/HTTP/URL decision contracts (#1016 cycle 2b P1).
test-decision: $(BUILD_DIR)/ezkey_challenge.o $(BUILD_DIR)/ezkey_sanitize.o \
		$(TEST_DIR)/test_decision_harness.c $(SRC_DIR)/ezkey_decision.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_decision_harness.c \
		$(BUILD_DIR)/ezkey_challenge.o $(BUILD_DIR)/ezkey_sanitize.o \
		-lpam -o $(BUILD_DIR)/test_decision_harness
	$(BUILD_DIR)/test_decision_harness

# Host-side: shared sshd-warn-global-maxauthtries-1.awk (Patrick #908 / #934 B1).
test-install-sshd-warn:
	./scripts/test-install-sshd-warn.sh

$(BUILD_DIR)/test_no_conv_client: $(TEST_DIR)/test_no_conv_client.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) $(TEST_DIR)/test_no_conv_client.c -lpam \
		-o $(BUILD_DIR)/test_no_conv_client

test: check-source all check-hardening test-filelog test-sanitize test-challenge \
		test-conf test-budget test-session test-diag test-http test-decision \
		test-install-sshd-warn $(BUILD_DIR)/test_no_conv_client
	@echo "test: compiled $(BUILD_DIR)/$(MODULE_NAME) + harnesses + hardening OK"

help:
	@echo "Available targets:"
	@echo "  all           - Build the PAM module"
	@echo "  debug         - Build with debug symbols"
	@echo "  install       - Install module and config (requires root)"
	@echo "  uninstall     - Remove module from system"
	@echo "  clean         - Remove build files"
	@echo "  check-pam     - Check if module is configured in PAM"
	@echo "  check-source  - Guard against /tmp debug logs (#818)"
	@echo "  check-hardening - readelf BIND_NOW + stack protector (#1016 C6)"
	@echo "  test-filelog  - Mode/hardlink/FIFO harness (#818 m2)"
	@echo "  test-sanitize - Sanitize space/=/high-byte harness (M1)"
	@echo "  test-challenge - Challenge 0..99 + display_ok harness"
	@echo "  test-conf     - Conf default + bool typo + overlong line (#1016 C3)"
	@echo "  test-budget   - AUTH_BUDGET 105 + rem-too-small planned-wait harness"
	@echo "  test-session  - final-verdict + interrupt cancel harness (#907)"
	@echo "  test-diag     - diag harness under timeout (#1011 / #1016 N5/T1/T2/P-a)"
	@echo "  test-http     - body cap / PROXY / json long / URL redact (#1016 C1–C8)"
	@echo "  test-install-sshd-warn - shared MaxAuthTries 1 awk (#908 / #934 B1)"
	@echo "  test          - check-source + compile + harnesses + hardening"
	@echo "  lint          - Run static code analysis"
