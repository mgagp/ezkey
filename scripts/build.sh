#!/bin/bash

set -e
set -x

mvn spotless:apply
mvn checkstyle:check
mvn clean
mvn install -DskipTests
mvn test -pl '!ezkey-tests'
