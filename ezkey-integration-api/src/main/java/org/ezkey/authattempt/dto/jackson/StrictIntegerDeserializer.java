/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Deserializer: StrictIntegerDeserializer
 * Description: Rejects non-integer JSON tokens for Integer fields (no float/string/boolean coercion).
 */

package org.ezkey.authattempt.dto.jackson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Field-scoped integer deserializer that rejects Jackson's default coercions.
 *
 * <p>Accepts only JSON integer number tokens. Floats ({@code 20.5}), strings ({@code "20"}),
 * booleans, and explicit {@code null} fail deserialization so Spring maps them to the same
 * generic HTTP 400 as other unreadable bodies (#895 Patrick B2). Absent properties are not
 * deserialized and remain {@code null} (server default applies).
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class StrictIntegerDeserializer extends ValueDeserializer<Integer> {

  @Override
  public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
    JsonToken token = p.currentToken();
    if (token == JsonToken.VALUE_NUMBER_INT) {
      JsonParser.NumberType numberType = p.getNumberType();
      if (numberType == JsonParser.NumberType.INT) {
        return p.getIntValue();
      }
      if (numberType == JsonParser.NumberType.LONG) {
        long value = p.getLongValue();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
          return (Integer)
              ctxt.handleWeirdNumberValue(
                  Integer.class, p.getNumberValue(), "Integer value out of 32-bit range");
        }
        return (int) value;
      }
    }
    return (Integer) ctxt.handleUnexpectedToken(Integer.class, p);
  }

  /**
   * Explicit JSON {@code null} must fail (same generic 400 path). Absent fields do not invoke this
   * method — they stay unset so the Integration create default applies.
   *
   * @param ctxt deserialization context
   * @return never returns normally
   */
  @Override
  public Integer getNullValue(DeserializationContext ctxt) {
    return ctxt.reportInputMismatch(
        Integer.class, "JSON null is not allowed; omit the field to use the server default");
  }
}
