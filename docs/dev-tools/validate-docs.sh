#!/bin/bash

# Ezkey Documentation Validation Script
# This script validates the documentation for consistency, broken links, and formatting

set -e

echo "🔍 Ezkey Documentation Validation"
echo "=================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS")
            echo -e "${GREEN}✅ $message${NC}"
            ;;
        "WARNING")
            echo -e "${YELLOW}⚠️  $message${NC}"
            ;;
        "ERROR")
            echo -e "${RED}❌ $message${NC}"
            ;;
        "INFO")
            echo -e "ℹ️  $message"
            ;;
    esac
}

# Check if required tools are installed
check_dependencies() {
    print_status "INFO" "Checking dependencies..."
    
    local missing_deps=()
    
    if ! command -v markdownlint &> /dev/null; then
        missing_deps+=("markdownlint")
    fi
    
    if ! command -v linkchecker &> /dev/null; then
        missing_deps+=("linkchecker")
    fi
    
    if ! command -v grep &> /dev/null; then
        missing_deps+=("grep")
    fi
    
    if [ ${#missing_deps[@]} -ne 0 ]; then
        print_status "ERROR" "Missing dependencies: ${missing_deps[*]}"
        print_status "INFO" "Install missing dependencies and try again"
        exit 1
    fi
    
    print_status "SUCCESS" "All dependencies are available"
}

# Validate markdown formatting
validate_markdown() {
    print_status "INFO" "Validating markdown formatting..."
    
    local errors=0
    
    # Check for markdown files
    local md_files=$(find docs/ -name "*.md" -type f)
    
    if [ -z "$md_files" ]; then
        print_status "WARNING" "No markdown files found in docs/"
        return 0
    fi
    
    # Run markdownlint
    if markdownlint $md_files --config docs/dev-tools/.markdownlint.json; then
        print_status "SUCCESS" "Markdown formatting is valid"
    else
        print_status "ERROR" "Markdown formatting issues found"
        errors=$((errors + 1))
    fi
    
    return $errors
}

# Check for broken internal links
check_internal_links() {
    print_status "INFO" "Checking internal links..."
    
    local errors=0
    local broken_links=()
    
    # Find all markdown files
    local md_files=$(find docs/ -name "*.md" -type f)
    
    for file in $md_files; do
        # Extract links from markdown files
        local links=$(grep -o '\[.*\]([^)]*)' "$file" | sed 's/.*(\([^)]*\)).*/\1/' | grep -v '^http')
        
        for link in $links; do
            # Skip external links and anchors
            if [[ $link == http* ]] || [[ $link == mailto:* ]] || [[ $link == \#* ]]; then
                continue
            fi
            
            # Check if linked file exists
            local target_file=""
            if [[ $link == /* ]]; then
                # Absolute path from project root
                target_file="$link"
            else
                # Relative path from current file
                local dir=$(dirname "$file")
                target_file="$dir/$link"
            fi
            
            if [ ! -f "$target_file" ]; then
                broken_links+=("$file: $link")
                errors=$((errors + 1))
            fi
        done
    done
    
    if [ $errors -eq 0 ]; then
        print_status "SUCCESS" "All internal links are valid"
    else
        print_status "ERROR" "Found $errors broken internal links:"
        for link in "${broken_links[@]}"; do
            echo "  - $link"
        done
    fi
    
    return $errors
}

# Check for TODO/FIXME items
check_todos() {
    print_status "INFO" "Checking for TODO/FIXME items..."
    
    local todos=$(grep -r "TODO\|FIXME\|XXX\|HACK" docs/ --include="*.md" || true)
    
    if [ -z "$todos" ]; then
        print_status "SUCCESS" "No TODO/FIXME items found"
        return 0
    else
        print_status "WARNING" "Found TODO/FIXME items:"
        echo "$todos" | while read -r line; do
            echo "  - $line"
        done
        return 1
    fi
}

# Check for outdated information
check_outdated() {
    print_status "INFO" "Checking for potentially outdated information..."
    
    local errors=0
    
    # Check for old localhost URLs
    local old_urls=$(grep -r "localhost:8080\|localhost:9080" docs/ --include="*.md" || true)
    if [ -n "$old_urls" ]; then
        print_status "WARNING" "Found localhost URLs (may need updating for production):"
        echo "$old_urls" | while read -r line; do
            echo "  - $line"
        done
    fi
    
    # Check for old file references
    local old_refs=$(grep -r "ezkey-docs/" docs/ --include="*.md" || true)
    if [ -n "$old_refs" ]; then
        print_status "ERROR" "Found references to old ezkey-docs directory:"
        echo "$old_refs" | while read -r line; do
            echo "  - $line"
        done
        errors=$((errors + 1))
    fi
    
    if [ $errors -eq 0 ]; then
        print_status "SUCCESS" "No obviously outdated information found"
    fi
    
    return $errors
}

# Check documentation structure
check_structure() {
    print_status "INFO" "Checking documentation structure..."
    
    local errors=0
    
    # Check for required files
    local required_files=(
        "docs/README.md"
        "docs/ARCHITECTURE.md"
        "docs/DEVELOPMENT.md"
        "docs/ENDPOINT.md"
        "docs/CRYPTO.md"
        "docs/MAINTENANCE.md"
    )
    
    for file in "${required_files[@]}"; do
        if [ ! -f "$file" ]; then
            print_status "ERROR" "Missing required file: $file"
            errors=$((errors + 1))
        fi
    done
    
    # Check for required directories
    local required_dirs=(
        "docs/monitoring"
        "docs/dev-tools"
    )
    
    for dir in "${required_dirs[@]}"; do
        if [ ! -d "$dir" ]; then
            print_status "ERROR" "Missing required directory: $dir"
            errors=$((errors + 1))
        fi
    done
    
    if [ $errors -eq 0 ]; then
        print_status "SUCCESS" "Documentation structure is valid"
    fi
    
    return $errors
}

# Generate documentation report
generate_report() {
    print_status "INFO" "Generating documentation report..."
    
    local report_file="docs/validation-report.md"
    
    cat > "$report_file" << EOF
# Documentation Validation Report

Generated on: $(date)

## Summary

- **Total markdown files**: $(find docs/ -name "*.md" -type f | wc -l)
- **Total lines of documentation**: $(find docs/ -name "*.md" -type f -exec wc -l {} + | tail -1 | awk '{print $1}')
- **Last validation**: $(date)

## File Structure

\`\`\`
$(tree docs/ -I 'node_modules|*.log' || find docs/ -type f | sort)
\`\`\`

## Validation Results

This report is generated by the documentation validation script.
Run \`docs/dev-tools/validate-docs.sh\` to regenerate.

EOF
    
    print_status "SUCCESS" "Report generated: $report_file"
}

# Main validation function
main() {
    local total_errors=0
    
    echo
    check_dependencies
    echo
    
    validate_markdown
    total_errors=$((total_errors + $?))
    echo
    
    check_internal_links
    total_errors=$((total_errors + $?))
    echo
    
    check_todos
    echo
    
    check_outdated
    total_errors=$((total_errors + $?))
    echo
    
    check_structure
    total_errors=$((total_errors + $?))
    echo
    
    generate_report
    echo
    
    # Final summary
    echo "=================================="
    if [ $total_errors -eq 0 ]; then
        print_status "SUCCESS" "Documentation validation completed successfully!"
        exit 0
    else
        print_status "ERROR" "Documentation validation completed with $total_errors errors"
        exit 1
    fi
}

# Run main function
main "$@"

