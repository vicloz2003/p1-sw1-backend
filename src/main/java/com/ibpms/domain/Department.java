package com.ibpms.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("departments")
public record Department(
        @Id String id,
        @Indexed(unique = true) String name,
        String description) {}

