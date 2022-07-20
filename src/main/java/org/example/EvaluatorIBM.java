package org.example;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.FhirPathExecutionException;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.util.ParametersUtil;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;

import com.ibm.fhir.path.FHIRPathBooleanValue;
import com.ibm.fhir.path.FHIRPathDecimalValue;
import com.ibm.fhir.path.FHIRPathElementNode;
import com.ibm.fhir.path.FHIRPathIntegerValue;
import com.ibm.fhir.path.FHIRPathNode;
import com.ibm.fhir.path.FHIRPathNumberValue;
import com.ibm.fhir.path.FHIRPathResourceNode;
import com.ibm.fhir.path.FHIRPathStringValue;
import com.ibm.fhir.path.FHIRPathSystemValue;
import com.ibm.fhir.path.evaluator.FHIRPathEvaluator;
import com.ibm.fhir.path.evaluator.FHIRPathEvaluator.EvaluationContext;
import com.ibm.fhir.path.exception.FHIRPathException;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collection;

import com.ibm.fhir.model.format.Format;
import com.ibm.fhir.model.generator.FHIRGenerator;
import com.ibm.fhir.model.parser.FHIRParser;
import com.ibm.fhir.model.resource.Resource;

public class EvaluatorIBM {

  private FhirContext ctx = FhirContext.forR4();

  @Operation(name = "fhirpath-ibm", idempotent = true, returnParameters = {
      @OperationParam(name = "resource", min = 1),
      @OperationParam(name = "expressions", typeName = "string", min = 1)
  })
  public IBaseParameters evaluate(HttpServletRequest theServletRequest,

      @OperationParam(name = "resource", min = 1) IBaseResource hapiResource,
      @OperationParam(name = "context") String contextExpression,
      @OperationParam(name = "expression") String expression,
      @OperationParam(name = "variables") Parameters.ParametersParameterComponent hapiVariables)
      throws FHIRPathException {

    IBaseParameters responseParameters = ParametersUtil.newInstance(ctx);
    responseParameters.setId("fhirpath");

    if (isNotBlank(expression)) {
      // echo the parameters used
      Parameters.ParametersParameterComponent paramsPart = (Parameters.ParametersParameterComponent) ParametersUtil
          .addParameterToParameters(ctx, responseParameters,
              "parameters");
      ParametersUtil.addPartString(ctx, paramsPart, "evaluator", "IBM-4.11.1");
      if (contextExpression != null)
        ParametersUtil.addPartString(ctx, paramsPart, "context", contextExpression);
      ParametersUtil.addPartString(ctx, paramsPart, "expression", expression);
      ParametersUtil.addPartResource(ctx, paramsPart, "resource", hapiResource);

      ca.uhn.fhir.parser.IParser hapiParser = ctx.newJsonParser();
      String jsonResource = hapiParser.encodeResourceToString(hapiResource);

      try {
        StringReader sr = new StringReader(jsonResource);
        Resource ibmResource = FHIRParser.parser(Format.JSON).parse(sr);
        FHIRPathEvaluator evaluator = FHIRPathEvaluator.evaluator();

        // locate all of the context objects
        java.util.ArrayList<FHIRPathNode> contextList = new java.util.ArrayList<FHIRPathNode>();
        if (contextExpression != null) {
          Collection<FHIRPathNode> contextOutputs;
          try {
            contextOutputs = evaluator.evaluate(ibmResource, contextExpression);
          } catch (FhirPathExecutionException e) {
            throw new InvalidRequestException(
                Msg.code(327) + "Error parsing FHIRPath expression: " + e.getMessage());
          }
          for (FHIRPathNode node : contextOutputs) {
            String path = node.path();
            contextList.add(node);
          }
        } else {
          contextList.add(FHIRPathResourceNode.resourceNode(ibmResource));
        }
        if (hapiVariables != null) {
          paramsPart.addPart(hapiVariables);
        }

        for (FHIRPathNode contextNode : contextList) {
          Collection<FHIRPathNode> result;
          EvaluationContext ibmCtx = new EvaluationContext();
          if (contextNode.isResourceNode()) {
            ibmCtx = new EvaluationContext(ibmResource);
          } else if (contextNode.isElementNode()) {
            ibmCtx = new EvaluationContext(contextNode.asElementNode().element());
          } else {
            ibmCtx = new EvaluationContext();
            throw new InvalidRequestException(
                Msg.code(327) + "Error executing FHIRPath expression (IBM): context cannot evaluate to a raw value - "
                    + contextNode.name());
          }

          // TODO: Add the Variables
          if (hapiVariables != null) {
            java.util.List<org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent> variableParts = hapiVariables
                .getPart();
            for (int i = 0; i < variableParts.size(); i++) {
              org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent part = variableParts.get(i);
              if (part.getResource() != null) {
                String jsonVarResource = hapiParser.encodeResourceToString(part.getResource());
                StringReader srVar = new StringReader(jsonVarResource);
                Resource ibmVarResource = FHIRParser.parser(Format.JSON).parse(srVar);
                ibmCtx.setExternalConstant(part.getName(), FHIRPathResourceNode.resourceNode(ibmVarResource));
              } else {
                if (part.getExtensionByUrl("http://fhir.forms-lab.com/StructureDefinition/json-value") != null) {
                  // this is not currently supported...
                  // ibmCtx.setExternalConstant(part.getName(), null);
                } else {
                  if (part.getValue() instanceof StringType)
                    ibmCtx.setExternalConstant(part.getName(),
                        FHIRPathStringValue.stringValue(((StringType) part.getValue()).getValue()));
                  else if (part.getValue() instanceof BooleanType)
                    ibmCtx.setExternalConstant(part.getName(),
                        FHIRPathBooleanValue.booleanValue(((BooleanType) part.getValue()).getValue()));
                  else if (part.getValue() instanceof DecimalType)
                    ibmCtx.setExternalConstant(part.getName(),
                        FHIRPathDecimalValue.decimalValue(((DecimalType) part.getValue()).getValue()));
                  else if (part.getValue() instanceof IntegerType)
                    ibmCtx.setExternalConstant(part.getName(),
                        FHIRPathIntegerValue.integerValue(((IntegerType) part.getValue()).getValue()));
                }
              }
            }
          }

          // Evaluate the expression
          result = evaluator.evaluate(ibmCtx, expression);

          Parameters.ParametersParameterComponent resultPart = (Parameters.ParametersParameterComponent) ParametersUtil
              .addParameterToParameters(ctx, responseParameters,
                  "result");
          if (contextExpression != null) {
            resultPart.setValue(new StringType(contextNode.path()));
          }

          for (FHIRPathNode node : result) {
            if (node instanceof FHIRPathResourceNode) {
              StringWriter sw = new StringWriter();
              FHIRGenerator.generator(Format.JSON, true).generate(node.asResourceNode().resource(), sw);
              ParametersUtil.addPartResource(ctx, resultPart, node.type().name(),
                  hapiParser.parseResource(sw.toString()));
            } else if (node instanceof FHIRPathElementNode) {
              FHIRPathElementNode elementNode = node.asElementNode();
              String elementType = elementNode.element().getClass().getName()
                  .replace("com.ibm.fhir.model.type.", "")
                  .replace("com.ibm.fhir.model.resource.", "");
              Parameters.ParametersParameterComponent part = new Parameters.ParametersParameterComponent();
              part.setName(elementType);

              com.ibm.fhir.model.type.Element element = elementNode.element();
              if (IsPrimitive(element)) {
                part.setValue(ConvertPrimitiveValue(element));
              } else {
                StringWriter sw = new StringWriter();
                FHIRGenerator.generator(Format.JSON).generate(element, sw);
                part.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                    new StringType(sw.toString()));
              }

              resultPart.addPart(part);
            } else if (node instanceof FHIRPathSystemValue) {
              FHIRPathSystemValue sv = node.asSystemValue();
              if (sv.isStringValue())
                ParametersUtil.addPart(ctx, resultPart, node.type().name(),
                    new org.hl7.fhir.r4.model.StringType(sv.asStringValue().string()));
              else if (sv.isBooleanValue())
                ParametersUtil.addPart(ctx, resultPart, node.type().name(),
                    new org.hl7.fhir.r4.model.BooleanType(sv.asBooleanValue()._boolean()));
              else if (sv.isNumberValue()) {
                FHIRPathNumberValue nv = sv.asNumberValue();
                if (nv.isDecimalValue())
                  ParametersUtil.addPart(ctx, resultPart, node.type().name(),
                      new org.hl7.fhir.r4.model.DecimalType(nv.decimal()));
                if (nv.isIntegerValue())
                  ParametersUtil.addPart(ctx, resultPart, node.type().name(),
                      new org.hl7.fhir.r4.model.IntegerType(nv.number().longValue()));
              } else {
                // unknown value
                ParametersUtil.addPart(ctx, resultPart, node.type().name(),
                    new org.hl7.fhir.r4.model.StringType(sv.asStringValue().string()));
              }
            } else {
              try {
                // ParametersUtil.addPart(ctx, resultPart, nextOutput.fhirType(), nextOutput);
              } catch (java.lang.IllegalArgumentException e) {
                // ParametersUtil.addParameterToParameters(ctx, resultPart,
                // nextOutput.fhirType());
              }
            }
          }
        }
        return responseParameters;
      } catch (Exception e) {
        throw new InvalidRequestException(
            Msg.code(327) + "Error executing FHIRPath expression (IBM): " + e.getMessage());
      }
    }
    return responseParameters;
  }

  private boolean IsPrimitive(com.ibm.fhir.model.type.Element element) {
    if (element instanceof com.ibm.fhir.model.type.String)
      return true;
    if (element instanceof com.ibm.fhir.model.type.Date)
      return true;
    if (element instanceof com.ibm.fhir.model.type.DateTime)
      return true;
    if (element instanceof com.ibm.fhir.model.type.Instant)
      return true;
    if (element instanceof com.ibm.fhir.model.type.Decimal)
      return true;
    if (element instanceof com.ibm.fhir.model.type.Integer)
      return true;
    if (element instanceof com.ibm.fhir.model.type.Boolean)
      return true;
    if (element instanceof com.ibm.fhir.model.type.Base64Binary)
      return true;
    if (element instanceof com.ibm.fhir.model.type.Uri)
      return true;
    if (element instanceof com.ibm.fhir.model.type.Time)
      return true;
    return false;
  }

  private org.hl7.fhir.r4.model.Type ConvertPrimitiveValue(com.ibm.fhir.model.type.Element element) {
    // do subtypes of string first
    if (element instanceof com.ibm.fhir.model.type.Id)
      return new org.hl7.fhir.r4.model.IdType(((com.ibm.fhir.model.type.String) element).getValue());
    if (element instanceof com.ibm.fhir.model.type.Code)
      return new org.hl7.fhir.r4.model.CodeType(((com.ibm.fhir.model.type.String) element).getValue());
    if (element instanceof com.ibm.fhir.model.type.Markdown)
      return new org.hl7.fhir.r4.model.MarkdownType(((com.ibm.fhir.model.type.String) element).getValue());
    // then string itself
    if (element instanceof com.ibm.fhir.model.type.String)
      return new org.hl7.fhir.r4.model.StringType(((com.ibm.fhir.model.type.String) element).getValue());

    // date
    if (element instanceof com.ibm.fhir.model.type.Date)
      return new org.hl7.fhir.r4.model.DateType(((com.ibm.fhir.model.type.Date) element).getValue().toString());

    // datetime
    if (element instanceof com.ibm.fhir.model.type.DateTime)
      return new org.hl7.fhir.r4.model.DateTimeType(((com.ibm.fhir.model.type.DateTime) element).getValue().toString());

    // instance
    if (element instanceof com.ibm.fhir.model.type.Instant)
      return new org.hl7.fhir.r4.model.InstantType(((com.ibm.fhir.model.type.Instant) element).getValue().toString());

    // time
    if (element instanceof com.ibm.fhir.model.type.Time)
      return new org.hl7.fhir.r4.model.TimeType(((com.ibm.fhir.model.type.Time) element).getValue().toString());

    // decimal
    if (element instanceof com.ibm.fhir.model.type.Decimal)
      return new org.hl7.fhir.r4.model.DecimalType(((com.ibm.fhir.model.type.Decimal) element).getValue().toString());

    if (element instanceof com.ibm.fhir.model.type.PositiveInt)
      return new org.hl7.fhir.r4.model.PositiveIntType(
          ((com.ibm.fhir.model.type.PositiveInt) element).getValue().toString());
    if (element instanceof com.ibm.fhir.model.type.UnsignedInt)
      return new org.hl7.fhir.r4.model.UnsignedIntType(
          ((com.ibm.fhir.model.type.UnsignedInt) element).getValue().toString());
    if (element instanceof com.ibm.fhir.model.type.Integer)
      return new org.hl7.fhir.r4.model.IntegerType(((com.ibm.fhir.model.type.Integer) element).getValue().toString());

    // boolean
    if (element instanceof com.ibm.fhir.model.type.Boolean)
      return new org.hl7.fhir.r4.model.BooleanType(((com.ibm.fhir.model.type.Boolean) element).getValue());

    // base64binary
    if (element instanceof com.ibm.fhir.model.type.Base64Binary)
      return new org.hl7.fhir.r4.model.Base64BinaryType(((com.ibm.fhir.model.type.Base64Binary) element).getValue());

    // do subtypes of Uri first
    if (element instanceof com.ibm.fhir.model.type.Canonical)
      return new org.hl7.fhir.r4.model.CanonicalType(((com.ibm.fhir.model.type.Uri) element).getValue());
    if (element instanceof com.ibm.fhir.model.type.Oid)
      return new org.hl7.fhir.r4.model.OidType(((com.ibm.fhir.model.type.Uri) element).getValue());
    if (element instanceof com.ibm.fhir.model.type.Uuid)
      return new org.hl7.fhir.r4.model.UuidType(((com.ibm.fhir.model.type.Uri) element).getValue());
    // then the uri itself
    if (element instanceof com.ibm.fhir.model.type.Uri)
      return new org.hl7.fhir.r4.model.UriType(((com.ibm.fhir.model.type.Uri) element).getValue());

    // should never get here anyway ;)
    return null;
  }
}
