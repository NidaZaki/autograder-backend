package com.autograder.autograder.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@Document("submission")
public class Submission {
    @MongoId
    String userId;
    Double grade;
    String studentCode;
    String date;
}
