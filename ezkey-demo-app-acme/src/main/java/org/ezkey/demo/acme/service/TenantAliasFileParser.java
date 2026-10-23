/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: TenantAliasFileParser
 * Description: Line-oriented parser for the external Acme tenant-alias mapping file.
 */

package org.ezkey.demo.acme.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the external tenant-alias mapping file used by the community Acme demo.
 *
 * <p>Line format: {@code alias|integration-key|secret-key|label}. Blank lines and {@code #}
 * comments are ignored. A trailing incomplete line (file not ending with a newline) is ignored so a
 * concurrent append without atomic replace cannot poison the map. Invalid lines are skipped with a
 * WARN that never includes the secret. When an alias appears twice, the last complete line wins.
 *
 * @author Ezkey contributors
 * @since 2026
 */
final class TenantAliasFileParser {

  private static final Logger LOG = LoggerFactory.getLogger(TenantAliasFileParser.class);

  /** Community alias is UUID-v4 truncated to 16 hex; longer codes remain accepted for ops flex. */
  static final Pattern ALIAS_PATTERN = Pattern.compile("^[0-9a-f]{16,64}$");

  private TenantAliasFileParser() {}

  /**
   * Parse outcome for one file read.
   *
   * @param mappings alias → slot fields (insertion order; last duplicate alias wins)
   * @param contentLineCount non-comment, non-blank lines seen (valid or invalid)
   * @param validLineCount lines accepted into {@code mappings}
   * @param rejectedLineCount invalid lines skipped
   */
  record ParseResult(
      Map<String, ParsedAlias> mappings,
      int contentLineCount,
      int validLineCount,
      int rejectedLineCount) {

    boolean hasUnusableContent() {
      return contentLineCount > 0 && validLineCount == 0;
    }
  }

  /**
   * One validated mapping line.
   *
   * @param alias lowercase hex capability token (URL path segment)
   * @param integrationKey Integration API integration key
   * @param secretKey Integration API secret key
   * @param label operator-facing label (never the alias)
   */
  record ParsedAlias(String alias, String integrationKey, String secretKey, String label) {}

  /**
   * Parses file text into alias mappings.
   *
   * @param text raw file content (may be empty)
   * @return parse result; never null
   */
  static ParseResult parse(String text) {
    Map<String, ParsedAlias> mappings = new LinkedHashMap<>();
    int contentLineCount = 0;
    int validLineCount = 0;
    int rejectedLineCount = 0;

    if (text == null || text.isEmpty()) {
      return new ParseResult(mappings, 0, 0, 0);
    }

    boolean endsWithNewline = text.endsWith("\n") || text.endsWith("\r\n") || text.endsWith("\r");
    String[] rawLines = text.split("\\R", -1);
    int lineLimit = rawLines.length;
    if (!endsWithNewline && lineLimit > 0) {
      // Incomplete last line (mid-append without atomic mv) — ignore it.
      lineLimit--;
      if (LOG.isDebugEnabled()) {
        LOG.debug("Ignoring incomplete trailing line in tenant-alias mapping file");
      }
    }

    for (int i = 0; i < lineLimit; i++) {
      String line = rawLines[i];
      if (line == null) {
        continue;
      }
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      contentLineCount++;
      ParsedAlias parsed = parseLine(trimmed, i + 1);
      if (parsed == null) {
        rejectedLineCount++;
        continue;
      }
      mappings.put(parsed.alias(), parsed);
      validLineCount++;
    }

    return new ParseResult(mappings, contentLineCount, validLineCount, rejectedLineCount);
  }

  private static ParsedAlias parseLine(String trimmed, int lineNumber) {
    String[] parts = trimmed.split("\\|", -1);
    if (parts.length != 4) {
      LOG.warn(
          "Skipping invalid tenant-alias line {}: expected alias|integration-key|secret-key|label"
              + " ({} fields)",
          lineNumber,
          parts.length);
      return null;
    }
    String alias = parts[0].trim();
    String integrationKey = parts[1].trim();
    String secretKey = parts[2].trim();
    String label = parts[3].trim();

    if (!ALIAS_PATTERN.matcher(alias).matches()) {
      LOG.warn(
          "Skipping invalid tenant-alias line {}: alias must be 16–64 lowercase hex characters",
          lineNumber);
      return null;
    }
    if (integrationKey.isBlank() || secretKey.isBlank() || label.isBlank()) {
      LOG.warn(
          "Skipping invalid tenant-alias line {}: integration-key, secret-key, and label must be"
              + " non-blank",
          lineNumber);
      return null;
    }
    // Never log alias, integration key, or secret.
    return new ParsedAlias(alias, integrationKey, secretKey, label);
  }
}
