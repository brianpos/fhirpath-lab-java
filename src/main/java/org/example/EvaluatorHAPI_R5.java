package org.example;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.fhirpath.FhirPathExecutionException;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.util.ParametersUtil;

import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.hapi.ctx.HapiWorkerContext;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.TypeDetails;
import org.hl7.fhir.r5.utils.FHIRPathEngine.IEvaluationContext;
import org.hl7.fhir.r5.utils.FHIRPathUtilityClasses.FunctionDetails;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.PathEngineException;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;

public class EvaluatorHAPI_R5 {
  public EvaluatorHAPI_R5(FhirContext context){
    ctx = context;
    _workerContext = new HapiWorkerContext(ctx, new DefaultProfileValidationSupport(ctx));
  }
  private FhirContext ctx;
  private IWorkerContext _workerContext;

  @Operation(name = "fhirpath-r5", idempotent = true, returnParameters = {
      @OperationParam(name = "resource", min = 1),
      @OperationParam(name = "expressions", typeName = "string", min = 1)
  })
  public IBaseParameters evaluate(HttpServletRequest theServletRequest,

      @OperationParam(name = "resource", min = 1) IBaseResource resource,
      @OperationParam(name = "context") String contextExpression,
      @OperationParam(name = "expression") String expression,
      @OperationParam(name = "variables") Parameters.ParametersParameterComponent variables) {

    IBaseParameters responseParameters = ParametersUtil.newInstance(ctx);
    responseParameters.setId("fhirpath");

    if (isNotBlank(expression)) {
      // echo the parameters used
      Parameters.ParametersParameterComponent paramsPart = (Parameters.ParametersParameterComponent) ParametersUtil
          .addParameterToParameters(ctx, responseParameters,
              "parameters");
      ParametersUtil.addPartString(ctx, paramsPart, "evaluator", FhirService.hapiVersion + " (r5)");
      if (contextExpression != null)
        ParametersUtil.addPartString(ctx, paramsPart, "context", contextExpression);
      ParametersUtil.addPartString(ctx, paramsPart, "expression", expression);
      ParametersUtil.addPartResource(ctx, paramsPart, "resource", resource);

      IFhirPath fhirPath = ctx.newFhirPath();
      IParser parser = ctx.newJsonParser();

      org.hl7.fhir.r5.utils.FHIRPathEngine engine = new org.hl7.fhir.r5.utils.FHIRPathEngine(
          _workerContext);
      FHIRPathTestEvaluationServices_R5 services = new FHIRPathTestEvaluationServices_R5();
      engine.setHostServices(services);

      // pass through all the variables
      if (variables != null) {
        paramsPart.addPart(variables);
        java.util.List<org.hl7.fhir.r5.model.Parameters.ParametersParameterComponent> variableParts = variables
            .getPart();
        for (int i = 0; i < variableParts.size(); i++) {
          org.hl7.fhir.r5.model.Parameters.ParametersParameterComponent part = variableParts.get(i);
          if (part.getResource() != null)
            services.mapVariables.put(part.getName(), part.getResource());
          else {
            if (part.getExtensionByUrl("http://fhir.forms-lab.com/StructureDefinition/json-value") != null) {
              // this is not currently supported...
              services.mapVariables.put(part.getName(), null);
            } else {
              services.mapVariables.put(part.getName(), part.getValue());
            }
          }
        }
      }

      // locate all of the context objects
      List<IBase> contextOutputs;
      if (contextExpression != null) {
        try {
          contextOutputs = fhirPath.evaluate(resource, contextExpression, IBase.class);
        } catch (FhirPathExecutionException e) {
          throw new InvalidRequestException(
              Msg.code(327) + "Error parsing FHIRPath expression: " + e.getMessage());
        }
      } else {
        contextOutputs = new java.util.ArrayList<IBase>();
        contextOutputs.add(resource);
      }

      for (int i = 0; i < contextOutputs.size(); i++) {
        org.hl7.fhir.r5.model.Base node = (org.hl7.fhir.r5.model.Base) contextOutputs.get(i);
        Parameters.ParametersParameterComponent resultPart = (Parameters.ParametersParameterComponent) ParametersUtil
            .addParameterToParameters(ctx, responseParameters,
                "result");
        if (contextExpression != null)
          resultPart.setValue(new StringType(String.format("%s[%d]", contextExpression, i)));

        List<org.hl7.fhir.r5.model.Base> outputs;
        try {
          services.traceToParameter = resultPart;
          outputs = engine.evaluate(node, expression);
        } catch (FhirPathExecutionException e) {
          throw new InvalidRequestException(
              Msg.code(327) + "Error parsing FHIRPath expression: " + e.getMessage());
        }

        for (IBase nextOutput : outputs) {
          if (nextOutput instanceof IBaseResource) {
            ParametersUtil.addPartResource(ctx, resultPart, nextOutput.fhirType(), (IBaseResource) nextOutput);
          } else if (nextOutput instanceof org.hl7.fhir.r5.model.BackboneElement) {
            Parameters.ParametersParameterComponent backboneValue = resultPart.addPart();
            backboneValue.setName(nextOutput.fhirType());
            String backboneJson = parser.encodeToString(nextOutput);
            backboneValue.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                new StringType(backboneJson));
          } else {
            try {
              ParametersUtil.addPart(ctx, resultPart, nextOutput.fhirType(), nextOutput);
            } catch (java.lang.IllegalArgumentException e) {
              // ParametersUtil.addParameterToParameters(ctx, resultPart,
              // nextOutput.fhirType());
            }
          }
        }
      }
    }
    return responseParameters;
  }

  private class FHIRPathTestEvaluationServices_R5 implements IEvaluationContext {
    public Parameters.ParametersParameterComponent traceToParameter;
    public java.util.HashMap<String, org.hl7.fhir.r5.model.Base> mapVariables;

    public FHIRPathTestEvaluationServices_R5() {
      mapVariables = new HashMap<String, org.hl7.fhir.r5.model.Base>();
    }

    @Override
    public List<org.hl7.fhir.r5.model.Base> resolveConstant(Object appContext, String name, boolean beforeContext)
        throws PathEngineException {
      if (mapVariables != null) {
        if (mapVariables.containsKey(name)) {
          List<org.hl7.fhir.r5.model.Base> result = new java.util.ArrayList<org.hl7.fhir.r5.model.Base>();
          org.hl7.fhir.r5.model.Base itemValue = mapVariables.get(name);
          if (itemValue != null)
            result.add(itemValue);
          return result;
        }
        // return null; // don't return null as the lack of the variable being defined
        // is an issue
      }
      throw new NotImplementedException(
          "Variable: `%" + name + "` was not provided");
    }

    @Override
    public TypeDetails resolveConstantType(Object appContext, String name) throws PathEngineException {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices_R5.resolveConstantType), when item is element: " + name);
    }

    @Override
    public boolean log(String argument, List<org.hl7.fhir.r5.model.Base> data) {
      if (traceToParameter != null) {
        Parameters.ParametersParameterComponent traceValue = traceToParameter.addPart();
        traceValue.setName("trace");
        traceValue.setValue(new StringType(argument));
        IParser parser = ctx.newJsonParser();

        for (IBase nextOutput : data) {
          if (nextOutput instanceof IBaseResource) {
            ParametersUtil.addPartResource(ctx, traceValue, nextOutput.fhirType(), (IBaseResource) nextOutput);
          } else if (nextOutput instanceof org.hl7.fhir.r5.model.BackboneElement) {
            Parameters.ParametersParameterComponent backboneValue = traceValue.addPart();
            backboneValue.setName(nextOutput.fhirType());
            String backboneJson = parser.encodeToString(nextOutput);
            backboneValue.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                new StringType(backboneJson));
          } else {
            // if ( netOutput instanceOf org.hl7.fhir.r5.model.BackboneElement)
            try {
              ParametersUtil.addPart(ctx, traceValue, nextOutput.fhirType(), nextOutput);
            } catch (java.lang.IllegalArgumentException e) {
              // ParametersUtil.addParameterToParameters(ctx, resultPart,
              // nextOutput.fhirType());
            }
          }
        }
        return true;
      }
      return false;
    }

    @Override
    public FunctionDetails resolveFunction(String functionName) {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices_R5.resolveFunction), when item is element (for " + functionName
              + ")");
    }

    @Override
    public TypeDetails checkFunction(Object appContext, String functionName, List<TypeDetails> parameters)
        throws PathEngineException {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices_R5.checkFunction), when item is element: " + functionName);
    }

    @Override
    public List<org.hl7.fhir.r5.model.Base> executeFunction(Object appContext, List<org.hl7.fhir.r5.model.Base> focus,
        String functionName, List<List<org.hl7.fhir.r5.model.Base>> parameters) {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices_R5.executeFunction), when item is element: " + functionName);
    }

    @Override
    public org.hl7.fhir.r5.model.Base resolveReference(Object appContext, String url,
        org.hl7.fhir.r5.model.Base refContext) throws FHIRException {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices_R5.resolveReference), when item is element");
    }

    @Override
    public boolean conformsToProfile(Object appContext, org.hl7.fhir.r5.model.Base item, String url)
        throws FHIRException {
      // if (url.equals("http://hl7.org/fhir/StructureDefinition/Patient"))
      // return true;
      // if (url.equals("http://hl7.org/fhir/StructureDefinition/Person"))
      // return false;
      throw new FHIRException("unknown profile " + url);
    }

    @Override
    public org.hl7.fhir.r5.model.ValueSet resolveValueSet(Object appContext, String url) {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices_R5.resolveReference), when item is element");
    }
  }
}
