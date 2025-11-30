#!/bin/bash

# API Security Test Script for Ezkey Admin API
# This script tests the security matrix and access control implementation

set -e

# Configuration
BASE_URL="http://localhost:8080"
API_KEY=""
ADMIN_TOKEN=""
TEST_INTEGRATION_ID=2
OTHER_INTEGRATION_ID=3

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to print test results
print_test_result() {
    local test_name="$1"
    local expected_status="$2"
    local actual_status="$3"
    local description="$4"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if [ "$expected_status" = "$actual_status" ]; then
        echo -e "${GREEN}✅ PASS${NC}: $test_name - $description"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ FAIL${NC}: $test_name - Expected $expected_status, got $actual_status - $description"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

# Function to make HTTP request and get status code
make_request() {
    local method="$1"
    local url="$2"
    local auth_header="$3"
    local data="$4"
    
    if [ -n "$data" ]; then
        curl -s -o /dev/null -w "%{http_code}" \
            -X "$method" \
            -H "Content-Type: application/json" \
            -H "$auth_header" \
            -d "$data" \
            "$url"
    else
        curl -s -o /dev/null -w "%{http_code}" \
            -X "$method" \
            -H "$auth_header" \
            "$url"
    fi
}

# Function to check if server is running
check_server() {
    echo -e "${BLUE}🔍 Checking if server is running...${NC}"
    
    local status=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null || echo "000")
    
    if [ "$status" = "200" ]; then
        echo -e "${GREEN}✅ Server is running${NC}"
        return 0
    else
        echo -e "${RED}❌ Server is not running or not accessible${NC}"
        echo -e "${YELLOW}Please start the Ezkey Admin API server and try again${NC}"
        return 1
    fi
}

# Function to test API key access to auth attempts (own integration)
test_api_key_own_auth_attempts() {
    echo -e "\n${BLUE}🧪 Testing API Key Access to Own Integration Auth Attempts${NC}"
    
    local auth_header="Authorization: Bearer $API_KEY"
    
    # Test GET auth attempts list
    local status=$(make_request "GET" "$BASE_URL/api/v1/auth-attempts" "$auth_header")
    print_test_result "API Key GET /auth-attempts" "200" "$status" "API key should access auth attempts list"
    
    # Test POST auth attempt creation
    local auth_data='{"enrollmentId": 1, "challengeRequired": false}'
    local status=$(make_request "POST" "$BASE_URL/api/v1/auth-attempts" "$auth_header" "$auth_data")
    print_test_result "API Key POST /auth-attempts" "201" "$status" "API key should create auth attempts"
}

# Function to test API key access to forbidden endpoints
test_api_key_forbidden_access() {
    echo -e "\n${BLUE}🧪 Testing API Key Access to Forbidden Endpoints${NC}"
    
    local auth_header="Authorization: Bearer $API_KEY"
    
    # Test enrollment endpoints
    local status=$(make_request "GET" "$BASE_URL/api/v1/enrollments" "$auth_header")
    print_test_result "API Key GET /enrollments" "403" "$status" "API key should be forbidden from enrollments"
    
    local enrollment_data='{"integrationId": 1, "enrollmentName": "test", "status": "CREATED"}'
    local status=$(make_request "POST" "$BASE_URL/api/v1/enrollments" "$auth_header" "$enrollment_data")
    print_test_result "API Key POST /enrollments" "403" "$status" "API key should be forbidden from creating enrollments"
    
    # Test integration endpoints
    local status=$(make_request "GET" "$BASE_URL/api/v1/integrations" "$auth_header")
    print_test_result "API Key GET /integrations" "403" "$status" "API key should be forbidden from integrations"
    
    # Test API key management endpoints
    local status=$(make_request "GET" "$BASE_URL/api/v1/api-keys/integration/$TEST_INTEGRATION_ID" "$auth_header")
    print_test_result "API Key GET /api-keys" "403" "$status" "API key should be forbidden from API key management"
    
    # Test audit log endpoints
    local status=$(make_request "GET" "$BASE_URL/api/v1/audit-logs" "$auth_header")
    print_test_result "API Key GET /audit-logs" "403" "$status" "API key should be forbidden from audit logs"
}

# Function to test admin access (baseline)
test_admin_access() {
    echo -e "\n${BLUE}🧪 Testing Admin Access (Baseline)${NC}"
    
    local auth_header="Authorization: Bearer $ADMIN_TOKEN"
    
    # Test enrollment endpoints
    local status=$(make_request "GET" "$BASE_URL/api/v1/enrollments" "$auth_header")
    print_test_result "Admin GET /enrollments" "200" "$status" "Admin should access enrollments"
    
    # Test integration endpoints
    local status=$(make_request "GET" "$BASE_URL/api/v1/integrations" "$auth_header")
    print_test_result "Admin GET /integrations" "200" "$status" "Admin should access integrations"
    
    # Test auth attempt endpoints
    local status=$(make_request "GET" "$BASE_URL/api/v1/auth-attempts" "$auth_header")
    print_test_result "Admin GET /auth-attempts" "200" "$status" "Admin should access auth attempts"
    
    # Test API key management endpoints
    local status=$(make_request "GET" "$BASE_URL/api/v1/api-keys/integration/$TEST_INTEGRATION_ID" "$auth_header")
    print_test_result "Admin GET /api-keys" "200" "$status" "Admin should access API key management"
    
    # Test audit log endpoints
    local status=$(make_request "GET" "$BASE_URL/api/v1/audit-logs" "$auth_header")
    print_test_result "Admin GET /audit-logs" "200" "$status" "Admin should access audit logs"
}

# Function to test unauthorized access
test_unauthorized_access() {
    echo -e "\n${BLUE}🧪 Testing Unauthorized Access${NC}"
    
    # Test without authentication
    local status=$(make_request "GET" "$BASE_URL/api/v1/enrollments" "")
    print_test_result "No Auth GET /enrollments" "401" "$status" "Unauthorized access should return 401"
    
    # Test with invalid token
    local auth_header="Authorization: Bearer invalid_token"
    local status=$(make_request "GET" "$BASE_URL/api/v1/enrollments" "$auth_header")
    print_test_result "Invalid Token GET /enrollments" "401" "$status" "Invalid token should return 401"
}

# Function to display usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --help              Show this help message"
    echo "  -u, --url URL           Base URL for the API (default: http://localhost:8080)"
    echo "  -k, --api-key KEY       API key for testing"
    echo "  -t, --admin-token TOKEN Admin token for testing"
    echo "  -i, --integration-id ID Test integration ID (default: 2)"
    echo "  --check-server-only     Only check if server is running"
    echo ""
    echo "Examples:"
    echo "  $0 --check-server-only"
    echo "  $0 -k 'your_api_key' -t 'your_admin_token'"
    echo "  $0 -u http://localhost:9080 -k 'your_api_key' -t 'your_admin_token'"
}

# Function to run all tests
run_all_tests() {
    echo -e "${BLUE}🚀 Starting API Security Tests${NC}"
    echo -e "Base URL: $BASE_URL"
    echo -e "Test Integration ID: $TEST_INTEGRATION_ID"
    echo ""
    
    # Check server first
    if ! check_server; then
        exit 1
    fi
    
    # Run test suites
    test_unauthorized_access
    test_api_key_forbidden_access
    test_api_key_own_auth_attempts
    test_admin_access
    
    # Display results
    echo -e "\n${BLUE}📊 Test Results Summary${NC}"
    echo -e "Total Tests: $TOTAL_TESTS"
    echo -e "${GREEN}Passed: $PASSED_TESTS${NC}"
    echo -e "${RED}Failed: $FAILED_TESTS${NC}"
    
    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "\n${GREEN}🎉 All tests passed! Security implementation is working correctly.${NC}"
        exit 0
    else
        echo -e "\n${RED}❌ Some tests failed. Please review the security implementation.${NC}"
        exit 1
    fi
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -u|--url)
            BASE_URL="$2"
            shift 2
            ;;
        -k|--api-key)
            API_KEY="$2"
            shift 2
            ;;
        -t|--admin-token)
            ADMIN_TOKEN="$2"
            shift 2
            ;;
        -i|--integration-id)
            TEST_INTEGRATION_ID="$2"
            shift 2
            ;;
        --check-server-only)
            check_server
            exit $?
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Check if required parameters are provided
if [ -z "$API_KEY" ] || [ -z "$ADMIN_TOKEN" ]; then
    echo -e "${YELLOW}⚠️  Warning: API key or admin token not provided. Some tests will be skipped.${NC}"
    echo -e "Use -k and -t options to provide authentication credentials."
    echo ""
fi

# Run tests
run_all_tests
