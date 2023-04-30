package com.autograder.autograder.repo;

import com.autograder.autograder.model.Code;
import com.autograder.autograder.model.UserRole;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRoleRepository extends MongoRepository<UserRole, ObjectId> {
}
