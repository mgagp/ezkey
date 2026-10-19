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
	$(BUILD_DIR)/ezkey_budget.o

.PHONY: all debug install uninstall clean check-pam check-source lint help \
	test test-filelog test-sanitize test-challenge test-budget

all: $(BUILD_DIR)/$(MODULE_NAME)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/pam_ezkey.o: $(SRC_DIR)/pam_ezkey.c $(SRC_DIR)/ezkey_config.h \
		$(SRC_DIR)/ezkey_filelog.h $(SRC_DIR)/ezkey_sanitize.h \
		$(SRC_DIR)/ezkey_challenge.h $(SRC_DIR)/ezkey_budget.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/pam_ezkey.c -o $(BUILD_DIR)/pam_ezkey.o

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
	@grep -q 'c >= 0x7f' $(SRC_DIR)/ezkey_sanitize.c
	@grep -q '/var/log/ezkey-pam' $(SRC_DIR)/ezkey_filelog.h
	@echo "check-source: openat/O_NOFOLLOW/O_NONBLOCK + sanitize>=0x7f + /var/log/ezkey-pam"

# Harness: 0644→0600, hardlink refused, FIFO non-blocking (#818 m2).
test-filelog: $(SRC_DIR)/ezkey_filelog.c $(TEST_DIR)/test_filelog_harness.c | $(BUILD_DIR)
	@$(TEST_DIR)/run-filelog-harness.sh "$(CC)" "$(CFLAGS)" "$(SRC_DIR)" "$(BUILD_DIR)"

# Harness: sanitize space/=/controls/high-bytes (M1 review).
test-sanitize: $(BUILD_DIR)/ezkey_sanitize.o $(TEST_DIR)/test_sanitize_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_sanitize_harness.c \
		$(BUILD_DIR)/ezkey_sanitize.o -o $(BUILD_DIR)/test_sanitize_harness
	$(BUILD_DIR)/test_sanitize_harness

# Harness: challenge 0..99 + display_ok (Christophe nits).
test-challenge: $(BUILD_DIR)/ezkey_challenge.o $(TEST_DIR)/test_challenge_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_challenge_harness.c \
		$(BUILD_DIR)/ezkey_challenge.o -lpam -o $(BUILD_DIR)/test_challenge_harness
	$(BUILD_DIR)/test_challenge_harness

# Harness: AUTH_BUDGET 105 + rem-too-small → −1 (Patrick #897 CHANGES).
test-budget: $(BUILD_DIR)/ezkey_budget.o $(TEST_DIR)/test_budget_harness.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -I$(SRC_DIR) $(TEST_DIR)/test_budget_harness.c \
		$(BUILD_DIR)/ezkey_budget.o -o $(BUILD_DIR)/test_budget_harness
	$(BUILD_DIR)/test_budget_harness

$(BUILD_DIR)/test_no_conv_client: $(TEST_DIR)/test_no_conv_client.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) $(TEST_DIR)/test_no_conv_client.c -lpam \
		-o $(BUILD_DIR)/test_no_conv_client

test: check-source all test-filelog test-sanitize test-challenge test-budget \
		$(BUILD_DIR)/test_no_conv_client
	@echo "test: compiled $(BUILD_DIR)/$(MODULE_NAME) + filelog + sanitize + challenge + budget OK"

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
	@echo "  test-budget   - AUTH_BUDGET 105 + rem-too-small planned-wait harness"
	@echo "  test          - check-source + compile + filelog + sanitize + challenge + budget"
	@echo "  lint          - Run static code analysis"
