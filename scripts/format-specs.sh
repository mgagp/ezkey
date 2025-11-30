#!/bin/bash

# Ezkey OpenAPI Specifications Formatter
# This script formats existing JSON specifications for better readability

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored output
print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to format a JSON file
format_json_file() {
    local file_path="$1"
    local file_name=$(basename "$file_path")
    
    if [ ! -f "$file_path" ]; then
        print_error "File not found: $file_path"
        return 1
    fi
    
    # Check if jq is available
    if ! command -v jq > /dev/null 2>&1; then
        print_error "jq is not installed. Please install jq to format JSON files."
        return 1
    fi
    
    # Validate JSON first
    if ! jq empty "$file_path" 2>/dev/null; then
        print_error "$file_name is not valid JSON"
        return 1
    fi
    
    # Create backup
    cp "$file_path" "$file_path.backup"
    
    # Format JSON
    if jq . "$file_path" > "$file_path.tmp" && mv "$file_path.tmp" "$file_path"; then
        print_success "Formatted $file_name"
        return 0
    else
        print_error "Failed to format $file_name"
        # Restore backup
        mv "$file_path.backup" "$file_path"
        return 1
    fi
}

# Main function
main() {
    local project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
    local specs_dir="$project_root/specs"
    local formatted_count=0
    local total_count=0
    
    echo "Ezkey OpenAPI Specifications Formatter"
    echo "======================================"
    echo ""
    
    # Check if jq is available
    if ! command -v jq > /dev/null 2>&1; then
        print_error "jq is not installed. Please install jq to format JSON files."
        echo ""
        echo "Installation instructions:"
        echo "  - Ubuntu/Debian: sudo apt-get install jq"
        echo "  - macOS: brew install jq"
        echo "  - Windows: Download from https://stedolan.github.io/jq/"
        exit 1
    fi
    
    # Format centralized specifications
    if [ -d "$specs_dir" ]; then
        echo "Formatting centralized specifications..."
        
        # Admin API
        if [ -f "$specs_dir/admin-api/openapi-spec.json" ]; then
            total_count=$((total_count + 1))
            if format_json_file "$specs_dir/admin-api/openapi-spec.json"; then
                formatted_count=$((formatted_count + 1))
            fi
        fi
        
        # Auth API
        if [ -f "$specs_dir/auth-api/openapi-spec.json" ]; then
            total_count=$((total_count + 1))
            if format_json_file "$specs_dir/auth-api/openapi-spec.json"; then
                formatted_count=$((formatted_count + 1))
            fi
        fi
    fi
    
    # Format project-specific specifications
    echo ""
    echo "Formatting project-specific specifications..."
    
    # Demo projects
    for project in "ezkey-demo-app-acme" "ezkey-demo-device"; do
        local spec_file="$project_root/$project/openapi-spec.json"
        if [ -f "$spec_file" ]; then
            total_count=$((total_count + 1))
            if format_json_file "$spec_file"; then
                formatted_count=$((formatted_count + 1))
            fi
        fi
    done
    
    # SDK specifications
    for spec in "admin-api-spec.json" "auth-api-spec.json"; do
        local spec_file="$project_root/ezkey-sdk/$spec"
        if [ -f "$spec_file" ]; then
            total_count=$((total_count + 1))
            if format_json_file "$spec_file"; then
                formatted_count=$((formatted_count + 1))
            fi
        fi
    done
    
    # Summary
    echo ""
    if [ $formatted_count -eq $total_count ] && [ $total_count -gt 0 ]; then
        print_success "All specifications formatted successfully! ($formatted_count/$total_count)"
        echo ""
        echo "Benefits of formatted JSON:"
        echo "  ✓ Better readability for humans"
        echo "  ✓ Easier to grep and search by sections"
        echo "  ✓ Better AI analysis and understanding"
        echo "  ✓ Cleaner Git diffs"
    elif [ $total_count -eq 0 ]; then
        print_warning "No JSON specification files found to format"
    else
        print_warning "Some specifications failed to format ($formatted_count/$total_count)"
    fi
}

# Run main function
main "$@"
