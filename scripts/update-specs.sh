#!/bin/bash

# Ezkey OpenAPI Specifications Update Script
# This script centralizes the management of OpenAPI specifications for all Ezkey projects

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPECS_DIR="$PROJECT_ROOT/specs"
ADMIN_API_URL="http://localhost:9080/api-docs"
AUTH_API_URL="http://localhost:8080/api-docs"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if a URL is accessible
check_api_availability() {
    local url=$1
    local api_name=$2
    
    if curl -s --connect-timeout 5 "$url" > /dev/null 2>&1; then
        return 0
    else
        print_error "$api_name is not accessible at $url"
        print_warning "Make sure the API is running before updating specifications"
        return 1
    fi
}

# Function to download and validate a specification
update_spec() {
    local api_name=$1
    local url=$2
    local spec_file="$SPECS_DIR/$api_name/openapi-spec.json"
    local backup_file="$spec_file.backup"
    
    print_status "Updating $api_name specification..."
    
    # Check if API is available
    if ! check_api_availability "$url" "$api_name"; then
        return 1
    fi
    
    # Create backup if file exists
    if [ -f "$spec_file" ]; then
        cp "$spec_file" "$backup_file"
        print_status "Backup created: $(basename "$backup_file")"
    fi
    
    # Download new specification
    if curl -s "$url" -o "$spec_file"; then
        # Validate and format JSON if jq is available
        if command -v jq > /dev/null 2>&1; then
            if jq empty "$spec_file" 2>/dev/null; then
                # Format JSON with proper indentation for better readability
                jq . "$spec_file" > "$spec_file.tmp" && mv "$spec_file.tmp" "$spec_file"
                print_success "$api_name specification updated, validated, and formatted"
            else
                print_error "Downloaded $api_name specification is not valid JSON"
                # Restore backup
                if [ -f "$backup_file" ]; then
                    mv "$backup_file" "$spec_file"
                    print_warning "Restored backup for $api_name"
                fi
                return 1
            fi
        else
            print_success "$api_name specification updated (validation and formatting skipped - jq not available)"
            print_warning "Consider installing jq for JSON validation and formatting"
        fi
        
        # Update project links
        update_project_links "$api_name" "$spec_file"
        
    else
        print_error "Failed to download $api_name specification"
        # Restore backup
        if [ -f "$backup_file" ]; then
            mv "$backup_file" "$spec_file"
            print_warning "Restored backup for $api_name"
        fi
        return 1
    fi
}

# Function to update links to projects
update_project_links() {
    local api_name=$1
    local spec_file=$2
    
    case $api_name in
        "admin-api")
            print_status "Updating admin-api links..."
            update_link "$spec_file" "ezkey-demo-app-acme/openapi-spec.json"
            update_link "$spec_file" "ezkey-sdk/admin-api-spec.json"
            update_link "$spec_file" "ezkey-admin-ui/openapi-spec.json"
            ;;
        "auth-api")
            print_status "Updating auth-api links..."
            update_link "$spec_file" "ezkey-demo-device/openapi-spec.json"
            update_link "$spec_file" "ezkey-sdk/auth-api-spec.json"
            update_link "$spec_file" "ezkey_mobile/openapi-spec.json"
            ;;
    esac
}

# Function to create links or copies based on OS
update_link() {
    local source=$1
    local target=$2
    local target_dir=$(dirname "$target")
    local running_in_wsl=false
    local windows_mounted_workspace=false

    if [ -n "$WSL_DISTRO_NAME" ] || grep -qi microsoft /proc/version 2>/dev/null; then
        running_in_wsl=true
    fi

    if [[ "$PROJECT_ROOT" =~ ^/mnt/[a-zA-Z]/ ]]; then
        windows_mounted_workspace=true
    fi
    
    # Ensure target directory exists
    mkdir -p "$target_dir"
    
    # On Windows and WSL workspaces mounted from Windows, prefer copies.
    # Native Windows tools (PowerShell, Node, Orval) can fail to resolve Linux symlinks.
    if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]] \
        || [ "$running_in_wsl" = true ] \
        || [ "$windows_mounted_workspace" = true ]; then
        rm -f "$target"
        cp "$source" "$target"
        print_status "Copied to $(basename "$target")"
    else
        # Linux/macOS: use symbolic link
        ln -sf "$(realpath "$source")" "$target"
        print_status "Linked to $(basename "$target")"
    fi
}

# Function to show help
show_help() {
    echo "Ezkey OpenAPI Specifications Update Script"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --admin-only    Update only admin-api specification"
    echo "  --auth-only     Update only auth-api specification"
    echo "  --all           Update all specifications (default)"
    echo "  --help          Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                    # Update all specifications"
    echo "  $0 --admin-only       # Update only admin-api"
    echo "  $0 --auth-only        # Update only auth-api"
    echo ""
echo "Prerequisites:"
echo "  - APIs must be running on localhost:9080 (admin) and localhost:8080 (auth)"
echo "  - curl must be available for downloading specifications"
echo "  - jq is optional but recommended for JSON validation and formatting"
}

# Main function
main() {
    local update_admin=true
    local update_auth=true
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --admin-only)
                update_admin=true
                update_auth=false
                shift
                ;;
            --auth-only)
                update_admin=false
                update_auth=true
                shift
                ;;
            --all)
                update_admin=true
                update_auth=true
                shift
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    print_status "Starting Ezkey OpenAPI specifications update..."
    print_status "Project root: $PROJECT_ROOT"
    print_status "Specs directory: $SPECS_DIR"
    
    # Ensure specs directory exists
    mkdir -p "$SPECS_DIR/admin-api" "$SPECS_DIR/auth-api"
    
    local success_count=0
    local total_count=0
    
    # Update admin-api specification
    if [ "$update_admin" = true ]; then
        total_count=$((total_count + 1))
        if update_spec "admin-api" "$ADMIN_API_URL"; then
            success_count=$((success_count + 1))
        fi
    fi
    
    # Update auth-api specification
    if [ "$update_auth" = true ]; then
        total_count=$((total_count + 1))
        if update_spec "auth-api" "$AUTH_API_URL"; then
            success_count=$((success_count + 1))
        fi
    fi
    
    # Summary
    echo ""
    if [ $success_count -eq $total_count ]; then
        print_success "All specifications updated successfully! ($success_count/$total_count)"
        print_status "You can now build demo projects and SDKs with updated specifications"
    else
        print_warning "Some specifications failed to update ($success_count/$total_count)"
        print_status "Check that APIs are running and accessible"
        exit 1
    fi
}

# Run main function with all arguments
main "$@"
