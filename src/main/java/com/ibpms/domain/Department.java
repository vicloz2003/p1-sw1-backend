package com.ibpms.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("departments")
public record Department(@Id String id, String name, String description) {}

