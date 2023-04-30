package com.autograder.autograder.controller;

import com.autograder.autograder.model.*;
import com.autograder.autograder.service.InstructorService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.util.List;

@RestController
@RequestMapping(value = "/instructor")
@CrossOrigin(origins = "*")
public class InstructorController {

    final InstructorService instructorService;

    public InstructorController(InstructorService instructorService) {
        this.instructorService = instructorService;
    }

    @PostMapping(value = "/submit", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<CodeResponse> testFunctions(@RequestPart("file") MultipartFile file, String userId, String studentCode) throws Exception {
        return ResponseEntity.ok(this.instructorService.testStudentCode(file, userId, studentCode));
    }

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<CodeResponse> postFunctions(@RequestPart("file") MultipartFile file,
                                                      @RequestPart("testFile") MultipartFile testFile,
                                                      String description, String deadline) throws Exception {
        return ResponseEntity.ok(this.instructorService.saveFunctions(file, testFile, description, deadline));
    }
    @GetMapping
    public ResponseEntity<StudentCodeResponse> getFunctions(){
        return ResponseEntity.ok(this.instructorService.getFunctionsList());
    }

    @GetMapping(value = "/code")
    public ResponseEntity<InstructorCode> getInstructorCode() throws FileNotFoundException {
        return ResponseEntity.ok(this.instructorService.getCode());
    }

    @GetMapping(value = "/studentsubmission")
    public ResponseEntity<List<Submission>> getStudentSubmission() {
        return ResponseEntity.ok(this.instructorService.getStudentSubmission());
    }

    @GetMapping(value = "/role")
    public ResponseEntity<List<UserRole>> getUserRoles() {
        return ResponseEntity.ok(this.instructorService.getUserRoles());
    }

}
