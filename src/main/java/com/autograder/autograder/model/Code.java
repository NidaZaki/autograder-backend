package com.autograder.autograder.model;

import lombok.Builder;
import lombok.Data;
import org.bson.types.Binary;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;
@Data
@Builder
@Document("code")
public class Code {
    @Id
    @Field("_id")
    public Object id;
    public List<String> functionsList;
    public Binary file;
    public Binary testFile;

    public String testFileName;

    public String description;

    public String deadline;

}
