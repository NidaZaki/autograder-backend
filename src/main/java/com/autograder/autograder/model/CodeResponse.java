package com.autograder.autograder.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CodeResponse {
    Status status;
    List<TestCaseGrouped> testCaseGrouped;
    List<TestCase> testCaseList;
    String instructorCode;
    String description;
    String output;
    Double score;
    String deadline;
}
