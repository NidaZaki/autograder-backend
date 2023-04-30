package com.autograder.autograder.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document("roles")
public class UserRole {
    String userName;
    String role;
}
