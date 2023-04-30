package com.autograder.autograder.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
@Data
@Builder
public class StudentCodeResponse {
    String deadline;
    public List<String> functionsList;

    String description;

}
