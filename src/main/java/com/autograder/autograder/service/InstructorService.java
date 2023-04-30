package com.autograder.autograder.service;

import com.autograder.autograder.model.*;
import com.autograder.autograder.repo.InstructorRepository;
import com.autograder.autograder.repo.SubmissionRepository;
import com.autograder.autograder.repo.UserRoleRepository;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.apache.commons.lang3.StringUtils;
import org.bson.BsonBinarySubType;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class InstructorService {
    final InstructorRepository instructorRepository;
    final SubmissionRepository submissionRepository;
    final UserRoleRepository userRoleRepository;

    public InstructorService(InstructorRepository instructorRepository,
                             SubmissionRepository submissionRepository,
                             UserRoleRepository userRoleRepository) {
        this.instructorRepository = instructorRepository;
        this.submissionRepository = submissionRepository;
        this.userRoleRepository = userRoleRepository;
    }

    public CodeResponse saveFunctions(MultipartFile file, MultipartFile testFile, String description, String deadline) throws Exception {
        String date = deadline;
        byte[] fileBytes = file.getBytes();
        byte[] testFileBytes = testFile.getBytes();

        InputStream fileContent = file.getInputStream();
        InputStream testFileContent = testFile.getInputStream();
        
        Path filePath = Paths.get(System.getProperty("user.dir").concat("\\src\\main\\java\\com\\autograder\\autograder\\temp"), file.getOriginalFilename());
        Path testFilePath = Paths.get(System.getProperty("user.dir").concat("\\src\\main\\java\\com\\autograder\\autograder\\temp"), testFile.getOriginalFilename());

        Files.copy(fileContent, filePath);
        Files.copy(testFileContent, testFilePath);

        String[] command = {"javac", "-d", "bin", "-cp", ".\\lib\\junit-platform-console-standalone-1.9.2.jar", filePath.toString(), testFilePath.toString()};

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();
        process.getOutputStream().close();

        Status status = null;
        List<Method> methodList;
        List<TestCase> testCaseList = new ArrayList<>();
        List<TestCaseGrouped> testCaseGroupedList = new ArrayList<>();
        List<PassedTestCase> testCaseArgumentList;
        StringBuilder outputLines = new StringBuilder();
        /**
         * Check if any errors or compilation errors encounter.
         */
        if( process.getErrorStream().read() != -1 ) {
            status = printErrors(Status.COMPILE_ERROR, process.getErrorStream(), outputLines);
            deleteTempFiles(filePath, testFilePath);
        }

        if( process.exitValue() == 0 ){
            try {
                process = new ProcessBuilder("java","-jar", ".\\lib\\junit-platform-console-standalone-1.9.2.jar",
                        "--details=tree", "--details-theme=ascii", "-cp",".;.\\bin","--select-class",
                        Objects.requireNonNull(testFile.getOriginalFilename()).split("\\.")[0]).start();

                File tempFile = new File(filePath.toString());

                file.transferTo(tempFile);

                methodList = getMethodSignatures(tempFile);

                File tempTestFile = new File(testFilePath.toString());

                testCaseList = getAssertionArguments(tempTestFile, methodList);

                status = print(Status.SUCCESS, process.getInputStream(), testCaseList);

                testCaseArgumentList =  getTestCaseArguments(tempTestFile, testCaseList);

                for (int i = 0; i < testCaseArgumentList.size(); i++) {

                    if(testCaseArgumentList.get(i).getTestCaseFunctionName().equals(testCaseList.get(i).getTestCaseFunctionName())
                            && testCaseList.get(i).getStatus().equals("[OK] SUCCESSFUL")){
                        //testCaseList.get(i).setAssertionArguments(testCaseArgumentList.get(i).getArguments());
                        StringBuilder sb = new StringBuilder();
                        int endIndex = testCaseArgumentList.get(i).getArguments().split(",").length - 1;
                        for(int j = 0; j < endIndex; j++){
                            sb.append(testCaseArgumentList.get(i).getArguments().split(",")[j]).append(",");
                        }
                        if(sb.toString().contains("{")){
                            String tempString = sb.toString().split("\\{")[1].split("\\}")[0];
                            System.out.println(tempString);
                            testCaseList.get(i).setExpectedOutput("[" + tempString + "]");
                            testCaseList.get(i).setActualOutput("[" + tempString + "]");
                        }
                        else{
                            testCaseList.get(i).setExpectedOutput(sb.substring(1,sb.length() - 1));
                            testCaseList.get(i).setActualOutput(sb.substring(1, sb.length() - 1));
                        }
                    }
                }

                Map<String, List<TestCase>> testCaseMap = testCaseList.stream().collect(Collectors.groupingBy(TestCase::getName));


                testCaseMap.forEach((name, testCases) -> {
                        testCaseGroupedList.add(TestCaseGrouped.builder()
                                .parentStatus(testCases.stream().anyMatch(TestCase::isStatusFailure))
                                .name(name)
                                .testCaseList(testCases)
                                .parentNumberOfFailedTest(testCases.stream().mapToInt(TestCase::getNumberOfFailedTests).sum())
                                .parentNumberOfPassedTest(testCases.size() - testCases.stream().mapToInt(TestCase::getNumberOfFailedTests).sum())
                                .build());
                });

                /** Check if RuntimeException or Errors encounter during execution then print errors on console
                 * Otherwise print Output
                 */
                if( process.getErrorStream().read() != -1 ){
                    deleteTempFiles(filePath, testFilePath);
                    status = print(Status.RUNTIME_ERROR, process.getErrorStream(), testCaseList);
                }
                this.instructorRepository.save(Code.builder().id(new ObjectId("6431abf33825fb60f10b6355")).
                        functionsList(methodList.stream().map(Method::getDeclaration).collect(Collectors.toList()))
                        .file(new Binary(BsonBinarySubType.BINARY, fileBytes))
                        .testFile(new Binary(BsonBinarySubType.BINARY, testFileBytes))
                        .testFileName(testFile.getOriginalFilename())
                        .description(description)
                        .deadline(deadline)
                        .build()
                );
                deleteTempFiles(filePath, testFilePath);
            } catch (Exception exception) {
                deleteTempFiles(filePath, testFilePath);
                throw new Exception(exception);
            }
        }
        return CodeResponse.builder()
                .status(status)
                .testCaseList(testCaseList)
                .testCaseGrouped(testCaseGroupedList)
                .description(description)
                .output(outputLines.toString())
                .deadline(deadline)
                .build();
    }

    public StudentCodeResponse getFunctionsList(){

        return StudentCodeResponse.builder()
                .functionsList(this.instructorRepository.findAll().get(0).getFunctionsList())
                .deadline(this.instructorRepository.findAll().get(0).getDeadline())
                .description(this.instructorRepository.findAll().get(0).getDescription())
                .build();
    }

    public CodeResponse testStudentCode(MultipartFile file, String userId, String studentCode) throws Exception {


        Code code = this.instructorRepository.findAll().get(0);

        // Create a BsonInputStream to read from the byte array
        byte[] bsonData = code.getTestFile().getData();

        // Create a ByteArrayInputStream to read from the byte array
        ByteArrayInputStream bais = new ByteArrayInputStream(bsonData);

        // Create a DataInputStream to read BSON document length
        DataInputStream dis = new DataInputStream(bais);

        // Create a FileOutputStream to write to a file
        File testFile = new File(code.getTestFileName());
        FileOutputStream fos = new FileOutputStream(testFile);

        // Create a ByteArrayOutputStream to write BSON data
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Write the BSON data to the ByteArrayOutputStream
        baos.write(bsonData);

        // Create an InputStream from the ByteArrayOutputStream
        InputStream inputStreamTestFile = new ByteArrayInputStream(baos.toByteArray());

        // Close the streams
        dis.close();
        fos.close();
        bais.close();
        baos.close();
        inputStreamTestFile.close();


        InputStream fileContent = file.getInputStream();
        InputStream testFileContent = inputStreamTestFile;

        Path filePath = Paths.get(System.getProperty("user.dir").concat("\\src\\main\\java\\com\\autograder\\autograder\\temp"), file.getOriginalFilename());
        Path testFilePath = Paths.get(System.getProperty("user.dir").concat("\\src\\main\\java\\com\\autograder\\autograder\\temp"), code.getTestFileName());

        Files.copy(fileContent, filePath);
        Files.copy(testFileContent, testFilePath);

        String[] command = {"javac", "-d", "bin", "-cp", ".\\lib\\junit-platform-console-standalone-1.9.2.jar", filePath.toString(), testFilePath.toString()};
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        System.out.println("Process Buildr" +processBuilder);
        Process process = processBuilder.start();
        System.out.println("Provess" + process);
        process.getOutputStream().close();

        Status status = null;
        float finalScore = 0;
        List<TestCase> testCaseList = new ArrayList<>();
        List<Method> methodList;
        List<TestCaseGrouped> testCaseGroupedList = new ArrayList<>();
        StringBuilder outputLines  = new StringBuilder();
        String user = userId;
        String studentCodeSubmission = studentCode;
        List<PassedTestCase> testCaseArgumentList;
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);

        boolean result = false;
        float lateSubmissionScore = 0;

        if( process.getErrorStream().read() != -1 ) {
            status = printErrors(Status.COMPILE_ERROR, process.getErrorStream(), outputLines);
            deleteTempFiles(filePath, testFilePath);
        }

        if( process.exitValue() == 0 ){
            try {
                process = new ProcessBuilder("java", "-jar", ".\\lib\\junit-platform-console-standalone-1.9.2.jar",
                        "--details=tree", "--details-theme=ascii",
                        "-cp", ".;.\\bin","--select-class",
                        Objects.requireNonNull(code.getTestFileName()).split("\\.")[0]).start();

                /** Check if RuntimeException or Errors encounter during execution then print errors on console
                 * Otherwise print Output
                 */

                File tempFile = new File(filePath.toString());

                file.transferTo(tempFile);

                methodList = getMethodSignatures(tempFile);

                File tempTestFile = new File(testFilePath.toString());

                testCaseList = getAssertionArguments(tempTestFile, methodList);

                status = print(Status.SUCCESS, process.getInputStream(), testCaseList);

                testCaseArgumentList =  getTestCaseArguments(tempTestFile, testCaseList);

                for (int i = 0; i < testCaseArgumentList.size(); i++) {

                    if(testCaseArgumentList.get(i).getTestCaseFunctionName().equals(testCaseList.get(i).getTestCaseFunctionName())
                            && testCaseList.get(i).getStatus().equals("[OK] SUCCESSFUL")){
                        //testCaseList.get(i).setAssertionArguments(testCaseArgumentList.get(i).getArguments());
                        StringBuilder sb = new StringBuilder();
                        int endIndex = testCaseArgumentList.get(i).getArguments().split(",").length - 1;
                        for(int j = 0; j < endIndex; j++){
                            sb.append(testCaseArgumentList.get(i).getArguments().split(",")[j]).append(",");
                        }
                        if(sb.toString().contains("{")){
                            String tempString = sb.toString().split("\\{")[1].split("\\}")[0];
                            System.out.println(tempString);
                            testCaseList.get(i).setExpectedOutput("[" + tempString + "]");
                            testCaseList.get(i).setActualOutput("[" + tempString + "]");
                        }
                        else{
                            testCaseList.get(i).setExpectedOutput(sb.substring(1,sb.length() - 1));
                            testCaseList.get(i).setActualOutput(sb.substring(1, sb.length() - 1));
                        }

                    }
                }

                Map<String, List<TestCase>> testCaseMap = testCaseList.stream().collect(Collectors.groupingBy(TestCase::getName));

                int parentNumberOfFailedTest = testCaseList.stream().mapToInt(TestCase::getNumberOfFailedTests).sum();
                int parentNumberOfPassedTestCase =  testCaseList.size() - parentNumberOfFailedTest ;
                int totalTestCases = parentNumberOfPassedTestCase + parentNumberOfFailedTest;
                finalScore = ((float) parentNumberOfPassedTestCase * 100) / (float) totalTestCases;

                if (!Objects.equals(this.instructorRepository.findAll().get(0).getDeadline(), "undefined")) {
                  result = compareDates(this.instructorRepository.findAll().get(0).getDeadline());
                  if (result) {
                    finalScore = calculateLateSubmissionScore(this.instructorRepository.findAll().get(0).getDeadline(), parentNumberOfPassedTestCase,totalTestCases );
                  }
                }

                testCaseMap.forEach((name, testCases) -> {
                    testCaseGroupedList.add(TestCaseGrouped.builder()
                            .parentStatus(testCases.stream().anyMatch(TestCase::isStatusFailure))
                            .name(name)
                            .testCaseList(testCases)
                            .parentNumberOfFailedTest(testCases.stream().mapToInt(TestCase::getNumberOfFailedTests).sum())
                            .parentNumberOfPassedTest(testCases.size() - testCases.stream().mapToInt(TestCase::getNumberOfFailedTests).sum())
                            .totalNumberOfTestCases(totalTestCases)
                            .build());
                });

                this.submissionRepository.save(Submission.builder()
                                .userId(user)
                                .studentCode(studentCodeSubmission)
                                .date(String.valueOf(new Date()).replace("EDT",""))
                                .grade(Double.valueOf(df.format(finalScore)) )
                        .build());

                if( process.getErrorStream().read() != -1 ){
                    deleteTempFiles(filePath, testFilePath);
                    status = print(Status.RUNTIME_ERROR, process.getErrorStream(), testCaseList);
                }
                deleteTempFiles(filePath, testFilePath);
            } catch (Exception exception) {
                deleteTempFiles(filePath, testFilePath);
                throw new Exception(exception);
            }
        }


        return CodeResponse.builder()
                .status(status)
                .testCaseList(testCaseList)
                .testCaseGrouped(testCaseGroupedList)
                .instructorCode(new String(code.getFile().getData()))
                .output(outputLines.toString())
                .score(Double.valueOf(df.format(finalScore)) )
                .build();
    }

    public boolean compareDates(String date) throws ParseException {
        boolean result = false;
        SimpleDateFormat sdf = new SimpleDateFormat("MM.dd.yyyy");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM.dd.yyyy");
        LocalDate localDate = LocalDate.now();
        Date firstDate = sdf.parse((dtf.format(localDate)));    // today's date
        Date secondDate = sdf.parse(date);

        if(firstDate.after(secondDate)){ // late submission
            result= true;
        }

        if(firstDate.before(secondDate)){
           result = false;
        }

       return result;
    }

    public float calculateLateSubmissionScore(String date1, Integer parentNumberOfPassedTestCase, Integer totalTestCases ) throws ParseException {

        SimpleDateFormat sdf = new SimpleDateFormat("MM.dd.yyyy");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM.dd.yyyy");
        LocalDate localDate = LocalDate.now();
        System.out.println(dtf.format(localDate));

        Date firstDate = sdf.parse(date1);
        Date secondDate = sdf.parse(dtf.format(localDate));

        long diffInMillies = Math.abs(firstDate.getTime() - secondDate.getTime());
        long diff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
        float score= 0;

        if(diff <= 3){
            float originalScore = (float) parentNumberOfPassedTestCase / (float) totalTestCases;
            score= (originalScore - (((float)(diff*10)/100) * originalScore) ) * 100;
        }
        return score;
    }

    public InstructorCode getCode() {
        Binary file = this.instructorRepository.findAll().get(0).getFile();
        Binary testFile = this.instructorRepository.findAll().get(0).getTestFile();
        return InstructorCode.builder()
                .code(new String(file.getData()))
                .testCode(new String(testFile.getData()))
                .description(this.instructorRepository.findAll().get(0).getDescription())
                .build();
    }
    private static void deleteTempFiles(Path filePath, Path testFilePath) throws IOException {
        Files.deleteIfExists(filePath);
        Files.deleteIfExists(testFilePath);
    }
    public static List<Method> getMethodSignatures(File file) {
        List<Method> methodList = new ArrayList<>();
            try {
                new VoidVisitorAdapter<Object>() {
                    @Override
                    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
                        super.visit(n, arg);
                        for(MethodDeclaration md : n.getMethods()){
                            System.out.println(" * Name -> * " + md.getNameAsString());
                            String declaration = md.getDeclarationAsString();
                            methodList.add(Method.builder().declaration(declaration).name(md.getNameAsString()).build());
                        }
                    }
                }.visit(StaticJavaParser.parse(file), null);
                System.out.println(); // empty line
            } catch (IOException e) {
                throw new RuntimeException(e);
        }
        return methodList;
    }
    public static List<TestCase> getAssertionArguments(File file, List<Method> methodList) {
        List<TestCase> testCaseList = new ArrayList<>();
        List<String> methodNamesList = methodList.stream().map(Method::getName).toList();

        try {
            new VoidVisitorAdapter<Object>() {
                @Override
                public void visit(ClassOrInterfaceDeclaration n, Object arg) {
                    super.visit(n, arg);
                    for(MethodDeclaration methodDeclaration : n.getMethods()){
                        List<MethodCallExpr> methodCalls = methodDeclaration.findAll(MethodCallExpr.class);
                        for (MethodCallExpr methodCall : methodCalls) {
                            List<Expression> arguments = methodCall.getArguments();
                            System.out.println("Method: " + methodDeclaration.getName() + ", " +
                                    "Invocation: " + methodCall.getName() + ", " +
                                    "Arguments: " + arguments);
                            if(methodNamesList.contains(methodCall.getName().asString())){
                                testCaseList.add(TestCase.builder().
                                        name(methodCall.getName().asString()).
                                        argument(arguments.toString()).
                                        testCaseFunctionName(methodDeclaration.getName().asString())
                                        .build());
                            }
                        }
                    }
                }
            }.visit(StaticJavaParser.parse(file), null);
            System.out.println(); // empty line
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return testCaseList;
    }
    public static List<PassedTestCase> getTestCaseArguments(File file, List<TestCase> testCaseList) {
        List<PassedTestCase> passedTestCaseList = new ArrayList<>();
        List<String> testMethodNames = getTestCaseMethodName(testCaseList);

        try {
            new VoidVisitorAdapter<Object>() {
                @Override
                public void visit(ClassOrInterfaceDeclaration n, Object arg) {
                    super.visit(n, arg);
                    for(MethodDeclaration methodDeclaration : n.getMethods()){
                        List<MethodCallExpr> methodCalls = methodDeclaration.findAll(MethodCallExpr.class);
                        for (MethodCallExpr methodCall : methodCalls) {
                            List<Expression> arguments = methodCall.getArguments();
                            System.out.println("Method: " + methodDeclaration.getName() + ", " +
                                    "Invocation: " + methodCall.getName() + ", " +
                                    "Arguments: " + arguments);
                            if(testMethodNames.contains( methodDeclaration.getName().asString()) && methodCall.getName().asString().contains("assert")){
                                passedTestCaseList.add(PassedTestCase.builder()
                                                .testCaseFunctionName(methodDeclaration.getName().asString())
                                                .arguments(arguments.toString())
                                        .build());
                            }
                        }
                    }
                }
            }.visit(StaticJavaParser.parse(file), null);
            System.out.println(); // empty line
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return passedTestCaseList;
    }
    public static List<String> getTestCaseMethodName(List<TestCase> testCaseList){

        List<String> testCaseMethodNameList = new ArrayList<>();
        for(TestCase testCase : testCaseList){
            testCaseMethodNameList.add(testCase.getTestCaseFunctionName());
        }
        return testCaseMethodNameList;
    }
    private static Status printErrors(Status status, InputStream input, StringBuilder output) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        String line;
        String splittedLine = "";
        while((line = in.readLine()) != null ){
            if(line.contains(".java")){
                splittedLine = line.split("\\.java")[1];
                StringBuilder sb = new StringBuilder(splittedLine);
                sb.insert(0, "Line");
                output.append("<br>").append(sb).append("<br>");
            }else{
                output.append(line).append("<br>");
            }
        }
        System.out.println(output);
        return status;
    }
    private static Status print(Status status, InputStream input, List<TestCase> testCases) throws IOException { 
        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        String line;
        int counter = 0;
        String relpacedLine;
        String finalLine = "";
        String successText = "[OK] SUCCESSFUL";
        String failureText = "[X] FAILED";
        while((line = in.readLine()) != null ){
            relpacedLine = line;
            System.out.println(line);
            if(!relpacedLine.equals("")) {
                String blah = line.split("\\(")[0];
                if (blah.length() > 25) {
                    finalLine = blah.substring(22);
                }

                for (int index = 0; index <= testCases.size() - 1; index++) {
                    if (finalLine.equals(testCases.get(index).getTestCaseFunctionName())) {
                        String testingStatus = line.split("\\(")[1];
                        if (testingStatus.contains("OK")) {
                            testCases.get(index).setStatus(successText);
                            testCases.get(index).setNumberOfFailedTests(0);
                            counter += 1;
                            break;
                        }
                        if (testingStatus.contains("X")) {

                            String spaceReplacedLine = testingStatus.replace("\u001B[0m \u001B[31m", "");
                            String furtherReplaceLine = spaceReplacedLine.replace("\u001B[0m", "");
                            testCases.get(index).setCaught(furtherReplaceLine.split("]")[1]);
                            String expectedOutput = furtherReplaceLine.split("but")[0].split(":")[1];
                            testCases.get(index).setExpectedOutput(StringUtils.substringBetween(expectedOutput, "<", ">"));
                            String actualOutput = furtherReplaceLine.split("was:")[1];
                            testCases.get(index).setActualOutput(StringUtils.substringBetween(actualOutput, "<", ">"));
                            testCases.get(index).setStatus(failureText);
                            counter += 1;
                            testCases.get(index).setNumberOfFailedTests(1);
                            break;

                        }
//                    while((line = in.readLine()) != null) {
//                        if (line.contains(successText)) {
//                            testCases.get(index).setStatus(successText);
//                            break;
//                        }
//                        if (line.contains("caught")) {
//                            testCases.get(index).setCaught(line.split("caught:")[1]);
//                            testCases.get(index).setStatus(failureText);
//                            break;
//                        }
//
//                    }
                    }
                }
            }
            if(counter == testCases.size()){
                break;
            }
        }
        in.close();
        return status;
    }
    public List<Submission> getStudentSubmission(){
        return this.submissionRepository.findAll();
    }
    public List<UserRole> getUserRoles(){
        return this.userRoleRepository.findAll();
    }
}
