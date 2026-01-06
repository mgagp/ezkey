#!/bin/bash
# Spring Profiles Configuration Verification Script
# 
# This script verifies that both Docker and Windows profiles are correctly configured
# for the Ezkey encryption key paths.
#
# Usage:
#   chmod +x verify-profiles.sh
#   ./verify-profiles.sh [docker|windows]
#
# Without arguments, runs all checks for both profiles.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PROFILE="${1:-all}"
ERRORS=0

echo ""
echo "=========================================="
echo "  Spring Profiles Verification Script"
echo "=========================================="
echo ""

# Function to check if file exists and contains a pattern
check_file_contains() {
    local file="$1"
    local pattern="$2"
    local description="$3"
    
    if [ ! -f "$file" ]; then
        echo -e "${RED}✗ MISSING${NC}: $file"
        ((ERRORS++))
        return 1
    fi
    
    if grep -q "$pattern" "$file"; then
        echo -e "${GREEN}✓${NC} $description"
        return 0
    else
        echo -e "${RED}✗${NC} $description (pattern not found: $pattern)"
        ((ERRORS++))
        return 1
    fi
}

# Function to check if file does NOT contain a pattern
check_file_not_contains() {
    local file="$1"
    local pattern="$2"
    local description="$3"
    
    if [ ! -f "$file" ]; then
        echo -e "${RED}✗ MISSING${NC}: $file"
        ((ERRORS++))
        return 1
    fi
    
    if ! grep -q "$pattern" "$file"; then
        echo -e "${GREEN}✓${NC} $description"
        return 0
    else
        echo -e "${RED}✗${NC} $description (pattern found: $pattern)"
        ((ERRORS++))
        return 1
    fi
}

# ============================================
# ADMIN API CHECKS
# ============================================
echo ""
echo "📁 Admin API Configuration"
echo "=========================================="

ADMIN_APP="${PROJECT_ROOT}/ezkey-admin-api/config/application.properties"
ADMIN_DOCKER="${PROJECT_ROOT}/ezkey-admin-api/config/application-docker.properties"
ADMIN_WINDOWS="${PROJECT_ROOT}/ezkey-admin-api/config/application-windows.properties"

if [ "$PROFILE" = "all" ] || [ "$PROFILE" = "docker" ]; then
    echo ""
    echo "🐳 Docker Profile:"
    check_file_contains "$ADMIN_DOCKER" "ezkey.encryption.master-key-file=/etc/ezkey/secrets/master.key" \
        "Master key path: /etc/ezkey/secrets/master.key"
    check_file_contains "$ADMIN_DOCKER" "ezkey.encryption.keyset.storage-mode=DATABASE" \
        "Keyset storage: DATABASE (distributed)"
    check_file_contains "$ADMIN_DOCKER" "ezkey.admin.initial.username=admin.docker" \
        "Initial admin username: admin.docker"
fi

if [ "$PROFILE" = "all" ] || [ "$PROFILE" = "windows" ]; then
    echo ""
    echo "🪟 Windows Profile:"
    check_file_contains "$ADMIN_WINDOWS" "ezkey.encryption.master-key-file=C:\\\\ProgramData\\\\ezkey\\\\secrets\\\\master.key" \
        "Master key path: C:\\ProgramData\\ezkey\\secrets\\master.key"
    check_file_contains "$ADMIN_WINDOWS" "ezkey.encryption.keyset.storage-mode=FILE" \
        "Keyset storage: FILE (single-instance)"
    check_file_contains "$ADMIN_WINDOWS" "ezkey.admin.initial.username=admin.windows" \
        "Initial admin username: admin.windows"
fi

if [ "$PROFILE" = "all" ]; then
    echo ""
    echo "✋ Default Configuration (should NOT contain hardcoded paths):"
    check_file_not_contains "$ADMIN_APP" "ezkey.encryption.master-key-file=/c/ProgramData" \
        "❌ Hardcoded /c/ProgramData path removed from default config"
    check_file_contains "$ADMIN_APP" "MUST be configured via profiles" \
        "Documentation added about profile-specific paths"
fi

# ============================================
# AUTH API CHECKS
# ============================================
echo ""
echo "📁 Auth API Configuration"
echo "=========================================="

AUTH_APP="${PROJECT_ROOT}/ezkey-auth-api/config/application.properties"
AUTH_DOCKER="${PROJECT_ROOT}/ezkey-auth-api/config/application-docker.properties"
AUTH_WINDOWS="${PROJECT_ROOT}/ezkey-auth-api/config/application-windows.properties"

if [ "$PROFILE" = "all" ] || [ "$PROFILE" = "docker" ]; then
    echo ""
    echo "🐳 Docker Profile:"
    check_file_contains "$AUTH_DOCKER" "ezkey.encryption.master-key-file=/etc/ezkey/secrets/master.key" \
        "Master key path: /etc/ezkey/secrets/master.key"
    check_file_contains "$AUTH_DOCKER" "ezkey.encryption.keyset.storage-mode=DATABASE" \
        "Keyset storage: DATABASE (synchronized)"
fi

if [ "$PROFILE" = "all" ] || [ "$PROFILE" = "windows" ]; then
    echo ""
    echo "🪟 Windows Profile:"
    check_file_contains "$AUTH_WINDOWS" "ezkey.encryption.master-key-file=C:\\\\ProgramData\\\\ezkey\\\\secrets\\\\master.key" \
        "Master key path: C:\\ProgramData\\ezkey\\secrets\\master.key"
    check_file_contains "$AUTH_WINDOWS" "ezkey.encryption.keyset.storage-mode=FILE" \
        "Keyset storage: FILE (single-instance)"
fi

if [ "$PROFILE" = "all" ]; then
    echo ""
    echo "✋ Default Configuration (should NOT contain hardcoded paths):"
    check_file_not_contains "$AUTH_APP" "ezkey.encryption.master-key-file=/c/ProgramData" \
        "❌ Hardcoded /c/ProgramData path removed from default config"
    check_file_contains "$AUTH_APP" "MUST be configured via profiles" \
        "Documentation added about profile-specific paths"
fi

# ============================================
# DOCUMENTATION CHECKS
# ============================================
if [ "$PROFILE" = "all" ]; then
    echo ""
    echo "📚 Documentation Files"
    echo "=========================================="
    
    echo ""
    echo "✓ Checking for migration and configuration guides:"
    
    SPRING_PROFILES_DOC="${PROJECT_ROOT}/docs/SPRING_PROFILES_CONFIGURATION.md"
    MIGRATION_DOC="${PROJECT_ROOT}/docs/MIGRATION_HARDCODED_PATHS_FIX.md"
    FIX_SUMMARY="${PROJECT_ROOT}/FIX_SUMMARY_SPRING_PROFILES.md"
    
    if [ -f "$SPRING_PROFILES_DOC" ]; then
        echo -e "${GREEN}✓${NC} SPRING_PROFILES_CONFIGURATION.md exists"
    else
        echo -e "${RED}✗${NC} SPRING_PROFILES_CONFIGURATION.md missing"
        ((ERRORS++))
    fi
    
    if [ -f "$MIGRATION_DOC" ]; then
        echo -e "${GREEN}✓${NC} MIGRATION_HARDCODED_PATHS_FIX.md exists"
    else
        echo -e "${RED}✗${NC} MIGRATION_HARDCODED_PATHS_FIX.md missing"
        ((ERRORS++))
    fi
    
    if [ -f "$FIX_SUMMARY" ]; then
        echo -e "${GREEN}✓${NC} FIX_SUMMARY_SPRING_PROFILES.md exists"
    else
        echo -e "${RED}✗${NC} FIX_SUMMARY_SPRING_PROFILES.md missing"
        ((ERRORS++))
    fi
fi

# ============================================
# KEY GENERATION SCRIPTS CHECK
# ============================================
if [ "$PROFILE" = "all" ]; then
    echo ""
    echo "🔑 Master Key Generation Scripts"
    echo "=========================================="
    
    echo ""
    echo "✓ Checking for master key generation scripts:"
    
    LINUX_KEYGEN="${PROJECT_ROOT}/scripts/generate-master-key.sh"
    WINDOWS_KEYGEN="${PROJECT_ROOT}/scripts/generate-master-key.ps1"
    DOCKER_KEYGEN="${PROJECT_ROOT}/docker/generate-encryption-keys.sh"
    
    if [ -f "$LINUX_KEYGEN" ]; then
        echo -e "${GREEN}✓${NC} scripts/generate-master-key.sh exists"
        check_file_contains "$LINUX_KEYGEN" "/etc/ezkey/secrets" \
            "  - Uses /etc/ezkey/secrets path (Linux)"
    else
        echo -e "${RED}✗${NC} scripts/generate-master-key.sh missing"
        ((ERRORS++))
    fi
    
    if [ -f "$WINDOWS_KEYGEN" ]; then
        echo -e "${GREEN}✓${NC} scripts/generate-master-key.ps1 exists"
    else
        echo -e "${RED}✗${NC} scripts/generate-master-key.ps1 missing"
        ((ERRORS++))
    fi
    
    if [ -f "$DOCKER_KEYGEN" ]; then
        echo -e "${GREEN}✓${NC} docker/generate-encryption-keys.sh exists"
        check_file_contains "$DOCKER_KEYGEN" "/etc/ezkey" \
            "  - Uses Docker volume paths"
    else
        echo -e "${RED}✗${NC} docker/generate-encryption-keys.sh missing"
        ((ERRORS++))
    fi
fi

# ============================================
# SUMMARY
# ============================================
echo ""
echo "=========================================="
echo "  Verification Summary"
echo "=========================================="
echo ""

if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✅ All checks passed!${NC}"
    echo ""
    echo "Configuration is ready:"
    echo "  🐳 Docker: ./docker/start.sh (uses 'docker' profile)"
    echo "  🪟 Windows: Set SPRING_PROFILES_ACTIVE=windows before running"
    echo "  📚 Documentation: See docs/SPRING_PROFILES_CONFIGURATION.md"
    echo ""
    exit 0
else
    echo -e "${RED}❌ Found $ERRORS error(s)${NC}"
    echo ""
    echo "Please review the configuration and fix the issues above."
    echo ""
    exit 1
fi
