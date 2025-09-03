#!/bin/bash

# Ezkey Code Quality Analysis Script
# This script runs all configured quality tools and generates reports

set -e

echo "🔍 Ezkey Code Quality Analysis"
echo "=============================="

# Check if we're in the project root
if [ ! -f "pom.xml" ]; then
    echo "❌ Error: Please run this script from the project root directory"
    exit 1
fi

# Clean and compile first
echo "📦 Building project..."
mvn clean compile -q

# Run quality tools
echo ""
echo "🔧 Running quality analysis tools..."

echo "  📋 Checkstyle..."
mvn checkstyle:checkstyle -q

echo "  🐛 SpotBugs..."
mvn spotbugs:spotbugs -q

echo "  📊 PMD..."
mvn pmd:pmd -q

echo ""
echo "📊 Quality Report Summary"
echo "========================"

# Count total violations
echo "SpotBugs findings by module:"
find . -name "spotbugsXml.xml" -exec bash -c 'module=$(echo {} | cut -d/ -f2); bugs=$(grep -c "BugInstance" {} || echo 0); echo "  $module: $bugs bugs"' \;

echo ""
echo "Checkstyle findings by module:"
find . -name "checkstyle-result.xml" -exec bash -c 'module=$(echo {} | cut -d/ -f2); violations=$(grep -c "<error" {} || echo 0); echo "  $module: $violations violations"' \;

echo ""
echo "PMD findings by module:"
find . -name "pmd.xml" -exec bash -c 'module=$(echo {} | cut -d/ -f2); violations=$(grep -c "<violation" {} || echo 0); echo "  $module: $violations violations"' \;

echo ""
echo "📁 Reports generated in:"
echo "  - target/checkstyle-result.xml (Checkstyle)"
echo "  - target/spotbugsXml.xml (SpotBugs)"
echo "  - target/pmd.xml (PMD)"
echo "  - target/site/checkstyle.html (Checkstyle HTML)"
echo "  - target/site/pmd.html (PMD HTML)"

echo ""
echo "✅ Quality analysis complete!"
echo ""
echo "🚀 Next Steps:"
echo "  1. Review CODE_IMPROVEMENT_PLAN.md for improvement roadmap"
echo "  2. Fix critical security issues in ezkey-core/SignatureService"
echo "  3. Address high-priority violations before lower-priority ones"