# Makefile for pam_ezkey module
# Compatible with Rocky Linux 9.6

CC = gcc
CFLAGS = -fPIC -Wall -Wextra -O2 -std=c99
LDFLAGS = -shared -lpam -lcurl -lcjson
MODULE_NAME = pam_ezkey.so
SRC_DIR = src
BUILD_DIR = build
INSTALL_DIR = /lib64/security

# Source files
SOURCES = $(SRC_DIR)/pam_ezkey.c
OBJECTS = $(BUILD_DIR)/pam_ezkey.o

# Default target
all: $(BUILD_DIR)/$(MODULE_NAME)

# Create build directory
$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

# Compile object files
$(BUILD_DIR)/pam_ezkey.o: $(SRC_DIR)/pam_ezkey.c $(SRC_DIR)/ezkey_config.h | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $(SRC_DIR)/pam_ezkey.c -o $(BUILD_DIR)/pam_ezkey.o

# Link the PAM module
$(BUILD_DIR)/$(MODULE_NAME): $(OBJECTS)
	$(CC) $(LDFLAGS) $(OBJECTS) -o $(BUILD_DIR)/$(MODULE_NAME)

# Debug build
debug: CFLAGS += -DDEBUG -g
debug: all

# Install the module (requires root)
install: $(BUILD_DIR)/$(MODULE_NAME)
	@echo "Installing PAM module to $(INSTALL_DIR)..."
	sudo cp $(BUILD_DIR)/$(MODULE_NAME) $(INSTALL_DIR)/
	sudo chmod 755 $(INSTALL_DIR)/$(MODULE_NAME)
	@echo "Module installed successfully!"
	@echo "Note: You still need to configure PAM manually."

# Uninstall the module
uninstall:
	@echo "Removing PAM module from $(INSTALL_DIR)..."
	sudo rm -f $(INSTALL_DIR)/$(MODULE_NAME)
	@echo "Module uninstalled successfully!"

# Clean build files
clean:
	rm -rf $(BUILD_DIR)

# Check PAM configuration
check-pam:
	@echo "Checking PAM configuration for SSH..."
	@if grep -q "pam_ezkey" /etc/pam.d/sshd; then \
		echo "✓ pam_ezkey found in SSH PAM configuration"; \
	else \
		echo "✗ pam_ezkey NOT found in SSH PAM configuration"; \
	fi

# Test module loading
test-load: install
	@echo "Testing module loading..."
	sudo /usr/sbin/pamtest-static -service=login -user=$$USER -authenticate

# Development helpers
lint:
	@echo "Running static analysis..."
	cppcheck --enable=all --std=c99 $(SRC_DIR)/

help:
	@echo "Available targets:"
	@echo "  all       - Build the PAM module"
	@echo "  debug     - Build with debug symbols"
	@echo "  install   - Install module to system (requires sudo)"
	@echo "  uninstall - Remove module from system (requires sudo)"
	@echo "  clean     - Remove build files"
	@echo "  check-pam - Check if module is configured in PAM"
	@echo "  lint      - Run static code analysis"
	@echo "  help      - Show this help message"

.PHONY: all debug install uninstall clean check-pam test-load lint help
