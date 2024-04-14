package org.example;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4b.model.ExpressionNode.Kind;

import com.fasterxml.jackson.annotation.JsonProperty;

interface IJsonNode extends Serializable {
    String getExpressionType();

    void setExpressionType(String expressionType);

    String getName();

    void setName(String name);

    List<JsonNode> getArguments();

    void setArguments(List<JsonNode> arguments);

    String getReturnType();

    void setReturnType(String returnType);
}

class JsonNode implements IJsonNode {
    private String expressionType;
    private String name;
    private List<JsonNode> arguments;
    private String returnType;

    public String getExpressionType() {
        return expressionType;
    }

    @JsonProperty("ExpressionType")
    @Override
    public void setExpressionType(String expressionType) {
        this.expressionType = expressionType;
    }

    @Override
    public String getName() {
        return name;
    }

    @JsonProperty("Name")
    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<JsonNode> getArguments() {
        return arguments;
    }

    @JsonProperty("Arguments")
    @Override
    public void setArguments(List<JsonNode> arguments) {
        this.arguments = arguments;
    }

    @Override
    public String getReturnType() {
        return returnType;
    }

    @JsonProperty("ReturnType")
    @Override
    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }
}