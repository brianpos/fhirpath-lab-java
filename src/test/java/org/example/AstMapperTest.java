package org.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.io.Files;
import java.nio.charset.Charset;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4b.context.IWorkerContext;
import org.hl7.fhir.r4b.hapi.ctx.HapiWorkerContext;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.Patient;

import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

class AstMapperTest {
    AstMapperTest() {
        var fhirContext = FhirContext.forR4B();
        _ctx = fhirContext;
        IWorkerContext workerContext = new HapiWorkerContext(fhirContext,
                new DefaultProfileValidationSupport(fhirContext));
        _workerContext = workerContext;

        _engine = new org.hl7.fhir.r4b.fhirpath.FHIRPathEngine(workerContext);
        // FHIRPathTestEvaluationServices services = new
        // FHIRPathTestEvaluationServices();
        // engine.setHostServices(services);

        _objectMapper = new ObjectMapper();
        _objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        _objectMapper.setSerializationInclusion(Include.NON_NULL);
        _prettyPrinter = new MyPrettyPrinter();
        _objectMapper.setDefaultPrettyPrinter(_prettyPrinter);
    }

    private FhirContext _ctx;
    private IWorkerContext _workerContext;

    org.hl7.fhir.r4b.fhirpath.FHIRPathEngine _engine;
    ObjectMapper _objectMapper;
    DefaultPrettyPrinter _prettyPrinter;

    private static String ReadJsonTestFile(String testName, String testSuffix) {
        return ReadTestFile(testName, testSuffix, "json");
    }

    private static String ReadTestFile(String testName, String testSuffix, String format) {
        try {
            String workingDir = System.getProperty("user.dir");
            return Files.asCharSource(new File(
                    workingDir + "/src/test/java/org/example/test-data/" + testName + "." + testSuffix + "." + format),
                    Charset.defaultCharset()).read();
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
            return null;
        }
    }

    private static void WriteJsonTestFile(String testName, String testSuffix, String content) {
        WriteTestFile(testName, testSuffix, "json", content);
    }

    private static void WriteTestFile(String testName, String testSuffix, String format, String content) {
        try {
            String workingDir = System.getProperty("user.dir");
            Files.asCharSink(new File(
                    workingDir + "/src/test/java/org/example/test-data/" + testName + "." + testSuffix + "." + format),
                    Charset.defaultCharset()).write(content);
        } catch (Exception e) {
            System.out.println(e);
            Assertions.fail(e.getMessage());
        }
    }

    private void testExpression(String testName, String expression) {
        var parseTree = _engine.parse(expression);
        SimplifiedExpressionNode simplifiedAST = SimplifiedExpressionNode.From(parseTree);
        JsonNode nodeParse = AstMapper.From(simplifiedAST);

        try {

            String jsonHapiAst = _objectMapper.writeValueAsString(simplifiedAST);
            String jsonFhirPathLabAst = _objectMapper.writeValueAsString(nodeParse);

            // Add your assertions here...
            assertEquals(ReadJsonTestFile(testName, "hapi"), jsonHapiAst, testName + ": HAPI AST incorrect");
            assertEquals(ReadJsonTestFile(testName, "lab"), jsonFhirPathLabAst, testName + ": FHIRPathLab AST incorrect");

        } catch (JsonProcessingException e) {
            System.out.println(e);
            Assertions.fail(e.getMessage());
        }
    }

    // This is similar to testExpression except that it will write the results as the expected output
    private void learnExpression(String testName, String expression) {
        var parseTree = _engine.parse(expression);
        SimplifiedExpressionNode simplifiedAST = SimplifiedExpressionNode.From(parseTree);
        JsonNode nodeParse = AstMapper.From(simplifiedAST);

        try {

            String jsonHapiAst = _objectMapper.writeValueAsString(simplifiedAST);
            String jsonFhirPathLabAst = _objectMapper.writeValueAsString(nodeParse);

            // Add your assertions here...
            WriteJsonTestFile(testName, "hapi", jsonHapiAst);
            WriteJsonTestFile(testName, "lab", jsonFhirPathLabAst);

        } catch (JsonProcessingException e) {
            System.out.println(e);
            Assertions.fail(e.getMessage());
        }
    }

    @Test
    public void operationTest1() {
        testExpression("operationTest1", "'a' + 'b' & 'c' + 'd'");
    }

    @Test
    public void operationTest2() {
        testExpression("operationTest2", "'a' + 'b' & 'c'");
    }

    @Test
    public void unaryTest1() {
        testExpression("unaryTest1", "-1");
    }

    @Test
    public void unaryTest2() {
        testExpression("unaryTest2", "+1");
    }

    @Test
    public void propertyChain() {
        testExpression("propertyChain", "Patient.name.given");
    }

    @Test
    public void propertyChainThenFunction() {
        testExpression("propertyChainThenFunction", "Patient.name.given.first()");
    }

    @Test
    public void functionTest1() {
        testExpression("functionTest1", "trace('trc').given.join(' ').combine(family).join(', ')");
    }

    @Test
    public void functionTest2() {
        testExpression("functionTest2", "trace('trc', family.first()).given.join(' ').combine(family).join(', ')");
    }

    @Test
    public void functionWithAParameter() {
        testExpression("functionWithAParameter", "select(name.first()).given");
    }

    @Test
    public void selectVariable() {
        testExpression("selectVariable", "select(%a)");
    }
}