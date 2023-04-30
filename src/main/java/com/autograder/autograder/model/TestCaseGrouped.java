package com.autograder.autograder.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TestCaseGrouped {
    String name;
    List<TestCase> testCaseList;
    boolean parentStatus;

    Integer parentNumberOfFailedTest;

    Integer parentNumberOfPassedTest;

    Integer totalNumberOfTestCases;
    List<PassedTestCase> passTestCaseList;

}
