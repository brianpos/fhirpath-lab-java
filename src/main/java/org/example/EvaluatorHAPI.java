package org.example;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.fhirpath.FhirPathExecutionException;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.util.ParametersUtil;

import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.r4.hapi.ctx.HapiWorkerContext;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TypeDetails;
import org.hl7.fhir.r4.utils.FHIRPathEngine.IEvaluationContext;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.PathEngineException;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;

public class EvaluatorHAPI {

  private FhirContext ctx = FhirContext.forR4();

  @Operation(name = "fhirpath", idempotent = true, returnParameters = {
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
      ParametersUtil.addPartString(ctx, paramsPart, "evaluator", "HAPI-6.0.1");
      if (contextExpression != null)
        ParametersUtil.addPartString(ctx, paramsPart, "context", contextExpression);
      ParametersUtil.addPartString(ctx, paramsPart, "expression", expression);
      ParametersUtil.addPartResource(ctx, paramsPart, "resource", resource);

      IFhirPath fhirPath = ctx.newFhirPath();
      // ca.uhn.fhir.parser.IParser parser = ctx.newJsonParser();

      org.hl7.fhir.r4.utils.FHIRPathEngine engine = new org.hl7.fhir.r4.utils.FHIRPathEngine(
          new HapiWorkerContext(ctx, new DefaultProfileValidationSupport(ctx)));
      FHIRPathTestEvaluationServices services = new FHIRPathTestEvaluationServices();
      engine.setHostServices(services);

      // pass through all the variables
      if (variables != null) {
        paramsPart.addPart(variables);
        java.util.List<org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent> variableParts = variables
            .getPart();
        for (int i = 0; i < variableParts.size(); i++) {
          org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent part = variableParts.get(i);
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
      java.util.ArrayList<String> contextList = new java.util.ArrayList<String>();
      if (contextExpression != null) {
        List<IBase> contextOutputs;
        try {
          contextOutputs = fhirPath.evaluate(resource, contextExpression, IBase.class);
        } catch (FhirPathExecutionException e) {
          throw new InvalidRequestException(
              Msg.code(327) + "Error parsing FHIRPath expression: " + e.getMessage());
        }

        for (int i = 0; i < contextOutputs.size(); i++) {
          // IBase nextOutput = contextOutputs.get(i);
          String path = String.format("%s[%d]", contextExpression, i);
          contextList.add(path);
        }
      } else {
        contextList.add("");
      }

      for (String key : contextList) {
        String itemExpression = expression;
        if (key != "")
          itemExpression = String.format("%s.select(%s)", key, expression);
        Parameters.ParametersParameterComponent resultPart = (Parameters.ParametersParameterComponent) ParametersUtil
            .addParameterToParameters(ctx, responseParameters,
                "result");
        if (key != "")
          resultPart.setValue(new StringType(key));

        List<org.hl7.fhir.r4.model.Base> outputs;
        try {
          services.traceToParameter = resultPart;
          // outputs = fhirPath.evaluate(resource, itemExpression, IBase.class);
          outputs = engine.evaluate((org.hl7.fhir.r4.model.Base) resource, itemExpression);
        } catch (FhirPathExecutionException e) {
          throw new InvalidRequestException(
              Msg.code(327) + "Error parsing FHIRPath expression: " + e.getMessage());
        }

        for (IBase nextOutput : outputs) {
          if (nextOutput instanceof IBaseResource) {
            ParametersUtil.addPartResource(ctx, resultPart, nextOutput.fhirType(), (IBaseResource) nextOutput);
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

  private class FHIRPathTestEvaluationServices implements IEvaluationContext {
    public Parameters.ParametersParameterComponent traceToParameter;
    public java.util.HashMap<String, org.hl7.fhir.r4.model.Base> mapVariables;

    public FHIRPathTestEvaluationServices() {
      mapVariables = new HashMap<String, org.hl7.fhir.r4.model.Base>();
    }

    @Override
    public org.hl7.fhir.r4.model.Base resolveConstant(Object appContext, String name, boolean beforeContext)
        throws PathEngineException {
      if (mapVariables != null) {
        if (mapVariables.containsKey(name)) {
          return mapVariables.get(name);
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
          "Not done yet (FHIRPathTestEvaluationServices.resolveConstantType), when item is element: " + name);
    }

    @Override
    public boolean log(String argument, List<org.hl7.fhir.r4.model.Base> data) {
      if (traceToParameter != null) {
        Parameters.ParametersParameterComponent traceValue = traceToParameter.addPart();
        traceValue.setName("trace");
        traceValue.setValue(new StringType(argument));

        for (IBase nextOutput : data) {
          if (nextOutput instanceof IBaseResource) {
            ParametersUtil.addPartResource(ctx, traceValue, nextOutput.fhirType(), (IBaseResource) nextOutput);
          } else if (nextOutput instanceof org.hl7.fhir.r4.model.BackboneElement) {
            ParametersUtil.addPart(ctx, traceValue, nextOutput.fhirType(), new StringType("<< Type Not Supported >>"));
          } else {
            // if ( netOutput instanceOf org.hl7.fhir.r4.model.BackboneElement)
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
          "Not done yet (FHIRPathTestEvaluationServices.resolveFunction), when item is element (for " + functionName
              + ")");
    }

    @Override
    public TypeDetails checkFunction(Object appContext, String functionName, List<TypeDetails> parameters)
        throws PathEngineException {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices.checkFunction), when item is element: " + functionName);
    }

    @Override
    public List<org.hl7.fhir.r4.model.Base> executeFunction(Object appContext, List<org.hl7.fhir.r4.model.Base> focus,
        String functionName, List<List<org.hl7.fhir.r4.model.Base>> parameters) {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices.executeFunction), when item is element: " + functionName);
    }

    @Override
    public org.hl7.fhir.r4.model.Base resolveReference(Object appContext, String url) throws FHIRException {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices.resolveReference), when item is element");
    }

    @Override
    public boolean conformsToProfile(Object appContext, org.hl7.fhir.r4.model.Base item, String url)
        throws FHIRException {
      // if (url.equals("http://hl7.org/fhir/StructureDefinition/Patient"))
      // return true;
      // if (url.equals("http://hl7.org/fhir/StructureDefinition/Person"))
      // return false;
      throw new FHIRException("unknown profile " + url);
    }

    @Override
    public org.hl7.fhir.r4.model.ValueSet resolveValueSet(Object appContext, String url) {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices.resolveReference), when item is element");
    }
  }
}
