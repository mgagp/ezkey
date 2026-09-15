# Makefile for pam_ezkey
# Build inside the module Docker image (Rocky Linux). Host builds also work
# when gcc, pam, libcurl, and cJSON development packages are installed.

CC = gcc
CFLAGS = -fPIC -Wall -Wextra -O2 -std=c99 -D_POSIX_C_SOURCE=200809L
LDFLAGS = -shared -lpam -lcurl -lcjson
MODULE_NAME = pam_ezkey.so
SRC_DIR = src
BUILD_DIR = build
INSTALL_DIR = /lib64/security

SOURCES = $(SRC_DIR)/pam_ezkey.c
OBJECTS = $(BUILD_DIR)/pam_ezkey.o

.PHONY: all debug install uninstall clean check-pam lint help

all: $(BUILD_DIR)/$(MODULE_NAME)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/pam_ezkey.o: $(SRC_DIR)/pam_ezkey.c $(SRC_DIR)/ezkey_config.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/pam_ezkey.c -o $(BUILD_DIR)/pam_ezkey.o

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

help:
	@echo "Available targets:"
	@echo "  all       - Build the PAM module"
	@echo "  debug     - Build with debug symbols"
	@echo "  install   - Install module and config (requires root)"
	@echo "  uninstall - Remove module from system"
	@echo "  clean     - Remove build files"
	@echo "  check-pam - Check if module is configured in PAM"
	@echo "  lint      - Run static code analysis"
