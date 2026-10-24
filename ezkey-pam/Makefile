# Makefile for pam_ezkey
# Build inside the module Docker image (Rocky or AL2023). Host builds also work
# when gcc, pam, libcurl, and cJSON development packages are installed.

CC = gcc
CFLAGS = -fPIC -Wall -Wextra -O2 -std=c99 -D_POSIX_C_SOURCE=200809L
LDFLAGS = -shared -lpam -lcurl -lcjson
MODULE_NAME = pam_ezkey.so
SRC_DIR = src
BUILD_DIR = build
TEST_DIR = test
INSTALL_DIR = /lib64/security

OBJECTS = $(BUILD_DIR)/pam_ezkey.o $(BUILD_DIR)/ezkey_filelog.o \
	$(BUILD_DIR)/ezkey_sanitize.o $(BUILD_DIR)/ezkey_challenge.o \
	$(BUILD_DIR)/ezkey_budget.o $(BUILD_DIR)/ezkey_session.o \
	$(BUILD_DIR)/ezkey_conf.o $(BUILD_DIR)/ezkey_diag.o

.PHONY: all debug install uninstall clean check-pam check-source lint help \
	test test-filelog test-sanitize test-challenge test-conf test-budget \
	test-session test-diag test-install-sshd-warn

all: $(BUILD_DIR)/$(MODULE_NAME)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/pam_ezkey.o: $(SRC_DIR)/pam_ezkey.c $(SRC_DIR)/ezkey_config.h \
		$(SRC_DIR)/ezkey_conf.h $(SRC_DIR)/ezkey_filelog.h \
		$(SRC_DIR)/ezkey_sanitize.h $(SRC_DIR)/ezkey_challenge.h \
		$(SRC_DIR)/ezkey_budget.h $(SRC_DIR)/ezkey_session.h \
		$(SRC_DIR)/ezkey_diag.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/pam_ezkey.c -o $(BUILD_DIR)/pam_ezkey.o

$(BUILD_DIR)/ezkey_diag.o: $(SRC_DIR)/ezkey_diag.c $(SRC_DIR)/ezkey_diag.h \
		$(SRC_DIR)/ezkey_config.h $(SRC_DIR)/ezkey_sanitize.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/ezkey_diag.c -o $(BUILD_DIR)/ezkey_diag.o

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
	$(CC) $(LDFLAGS) $(OBJECTS) -o $(BUILD_DIR)/$(MODULE_NAME)

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
	@grep -qE '"stage=%s' $(SRC_DIR)/ezkey_diag.c
	@grep -qE 'LOG_AUTHPRIV[[:space:]]*\|[[:space:]]*LOG_INFO' $(SRC_DIR)/ezkey_diag.c
	@grep -qE 'ezkey_diag_emit_signal_safe[[:space:]]*\(' $(SRC_DIR)/pam_ezkey.c
	@grep -qE 'ezkey_in_wait[[:space:]]*!=' $(SRC_DIR)/pam_ezkey.c
	@grep -qE 'raise[[:space:]]*\([[:space:]]*SIGTERM[[:space:]]*\)' $(SRC_DIR)/pam_ezkey.c
	@grep -qE 'saved_errno' $(SRC_DIR)/pam_ezkey.c
	# Patrick B2 (#982): match the real pam_prompt call — comments alone must not pass.
	@grep -qE 'pam_prompt[[:space:]]*\([^)]*PAM_PROMPT_ECHO_ON' $(SRC_DIR)/pam_ezkey.c
	@if grep -nE 'pam_prompt[[:space:]]*\([^)]*PAM_PROMPT_ECHO_OFF' $(SRC_DIR)/pam_ezkey.c; then \
		echo "error: pam_prompt(..., PAM_PROMPT_ECHO_OFF) must not remain (#982 tier 0)" >&2; exit 1; \
	fi
	@if grep -nE '(^|[^_[:alnum:]])pam_info[[:space:]]*\(' $(SRC_DIR)/pam_ezkey.c; then \
		echo "error: pam_info() must not appear in pam_ezkey.c (#941 — use pam_prompt)" >&2; exit 1; \
	fi
	@echo "check-source: openat/O_NOFOLLOW/O_NONBLOCK + sanitize + #941/#975/#982/#995 prompt guards"

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
test-diag: $(BUILD_DIR)/ezkey_diag.o $(BUILD_DIR)/ezkey_sanitize.o \
		$(TEST_DIR)/test_diag_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_diag_harness.c \
		$(BUILD_DIR)/ezkey_diag.o $(BUILD_DIR)/ezkey_sanitize.o \
		-o $(BUILD_DIR)/test_diag_harness
	$(BUILD_DIR)/test_diag_harness

# Host-side: shared sshd-warn-global-maxauthtries-1.awk (Patrick #908 / #934 B1).
test-install-sshd-warn:
	./scripts/test-install-sshd-warn.sh

$(BUILD_DIR)/test_no_conv_client: $(TEST_DIR)/test_no_conv_client.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) $(TEST_DIR)/test_no_conv_client.c -lpam \
		-o $(BUILD_DIR)/test_no_conv_client

test: check-source all test-filelog test-sanitize test-challenge test-conf \
		test-budget test-session test-diag test-install-sshd-warn \
		$(BUILD_DIR)/test_no_conv_client
	@echo "test: compiled $(BUILD_DIR)/$(MODULE_NAME) + filelog + sanitize + challenge + conf + budget + session + diag + install-sshd-warn OK"

help:
	@echo "Available targets:"
	@echo "  all           - Build the PAM module"
	@echo "  debug         - Build with debug symbols"
	@echo "  install       - Install module and config (requires root)"
	@echo "  uninstall     - Remove module from system"
	@echo "  clean         - Remove build files"
	@echo "  check-pam     - Check if module is configured in PAM"
	@echo "  check-source  - Guard against /tmp debug logs (#818)"
	@echo "  test-filelog  - Mode/hardlink/FIFO harness (#818 m2)"
	@echo "  test-sanitize - Sanitize space/=/high-byte harness (M1)"
	@echo "  test-challenge - Challenge 0..99 + display_ok harness"
	@echo "  test-conf     - Conf default + bool typo fallback (#995 B1/m1)"
	@echo "  test-budget   - AUTH_BUDGET 105 + rem-too-small planned-wait harness"
	@echo "  test-session  - final-verdict + interrupt cancel harness (#907)"
	@echo "  test-diag     - attempt-line fields + signal-safe emit (#1011)"
	@echo "  test-install-sshd-warn - shared MaxAuthTries 1 awk (#908 / #934 B1)"
	@echo "  test          - check-source + compile + harnesses (incl. session/diag)"
	@echo "  lint          - Run static code analysis"
