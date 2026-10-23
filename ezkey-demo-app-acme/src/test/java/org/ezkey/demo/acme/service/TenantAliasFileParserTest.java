/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TenantAliasFileParserTest
 * Description: Line parsing rules for the external tenant-alias mapping file.
 */

package org.ezkey.demo.acme.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.demo.acme.service.TenantAliasFileParser.ParseResult;
import org.ezkey.demo.acme.service.TenantAliasFileParser.ParsedAlias;
import org.junit.jupiter.api.Test;

class TenantAliasFileParserTest {

  private static final String ALIAS_A = "0123456789abcdef";
  private static final String ALIAS_B = "fedcba9876543210";

  @Test
  void shouldParseValidLinesAndIgnoreComments() {
    String text =
        """
        # header
        %s|ikey-a|skey-a|Northwind Portal

        %s|ikey-b|skey-b|Partner Demo
        """
            .formatted(ALIAS_A, ALIAS_B);

    ParseResult result = TenantAliasFileParser.parse(text);
    assertThat(result.validLineCount()).isEqualTo(2);
    assertThat(result.rejectedLineCount()).isZero();
    assertThat(result.mappings()).containsOnlyKeys(ALIAS_A, ALIAS_B);
    ParsedAlias a = result.mappings().get(ALIAS_A);
    assertThat(a.integrationKey()).isEqualTo("ikey-a");
    assertThat(a.label()).isEqualTo("Northwind Portal");
  }

  @Test
  void shouldLetLastDuplicateAliasWin() {
    String text =
        """
        %s|ikey-old|skey-old|Old Label
        %s|ikey-new|skey-new|New Label
        """
            .formatted(ALIAS_A, ALIAS_A);

    ParseResult result = TenantAliasFileParser.parse(text);
    assertThat(result.mappings()).hasSize(1);
    assertThat(result.mappings().get(ALIAS_A).integrationKey()).isEqualTo("ikey-new");
    assertThat(result.mappings().get(ALIAS_A).label()).isEqualTo("New Label");
  }

  @Test
  void shouldIgnoreIncompleteTrailingLineWithoutNewline() {
    String text =
        ALIAS_A + "|ikey-a|skey-a|Complete\n" + ALIAS_B + "|ikey-partial|skey-partial|Incomplete";

    ParseResult result = TenantAliasFileParser.parse(text);
    assertThat(result.mappings()).containsOnlyKeys(ALIAS_A);
    assertThat(result.mappings()).doesNotContainKey(ALIAS_B);
  }

  @Test
  void shouldSkipInvalidLinesWithoutAcceptingUnusableFile() {
    String text =
        """
        not-hex|ikey|skey|Bad
        %s|ikey-only
        """
            .formatted(ALIAS_A);

    ParseResult result = TenantAliasFileParser.parse(text);
    assertThat(result.validLineCount()).isZero();
    assertThat(result.contentLineCount()).isEqualTo(2);
    assertThat(result.hasUnusableContent()).isTrue();
  }

  @Test
  void shouldAcceptEmptyOrCommentOnlyFile() {
    ParseResult empty = TenantAliasFileParser.parse("");
    assertThat(empty.hasUnusableContent()).isFalse();
    assertThat(empty.mappings()).isEmpty();

    ParseResult comments = TenantAliasFileParser.parse("# only\n\n# more\n");
    assertThat(comments.hasUnusableContent()).isFalse();
    assertThat(comments.mappings()).isEmpty();
  }
}
