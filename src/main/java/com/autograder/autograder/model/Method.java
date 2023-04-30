package com.autograder.autograder.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Method {
    public String declaration;
    public String name;
}
