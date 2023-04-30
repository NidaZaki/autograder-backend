package com.autograder.autograder.repo;

import com.autograder.autograder.model.Code;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InstructorRepository extends MongoRepository<Code, ObjectId> {
}
