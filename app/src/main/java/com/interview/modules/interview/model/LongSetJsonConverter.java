package com.interview.modules.interview.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashSet;
import java.util.Set;

@Converter
public class LongSetJsonConverter implements AttributeConverter<Set<Long>, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<LinkedHashSet<Long>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(Set<Long> value) {
    try {
      return OBJECT_MAPPER.writeValueAsString(value == null ? Set.of() : value);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to serialize knowledge base ids", ex);
    }
  }

  @Override
  public Set<Long> convertToEntityAttribute(String value) {
    if (value == null || value.isBlank()) {
      return new LinkedHashSet<>();
    }
    try {
      return OBJECT_MAPPER.readValue(value, TYPE);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to deserialize knowledge base ids", ex);
    }
  }
}
