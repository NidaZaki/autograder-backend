package com.autograder.autograder.model;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InstructorCode {
    String code;
    String testCode;

    String description;
}
