package com.autograder.autograder.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PassedTestCase {

    String testCaseFunctionName;
    String arguments;
}
