package com.autograder.autograder.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TestCase {
    String name;
    String argument;

    String testCaseFunctionName;
    String caught;
    String expectedOutput;
    String actualOutput;

    Integer numberOfFailedTests;
    String status;

    String assertionArguments;



    public boolean isStatusFailure() {
        return this.getStatus().contains("[X]");
    }
}
