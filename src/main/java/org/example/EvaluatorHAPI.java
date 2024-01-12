package org.example;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.FhirPathExecutionException;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.util.ParametersUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.r4b.elementmodel.Manager;
import org.hl7.fhir.r4b.context.IWorkerContext;
import org.hl7.fhir.r4b.model.Base;
import org.hl7.fhir.r4b.model.Coding;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.StructureMap;
import org.hl7.fhir.r4b.model.StructureDefinition;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.r4b.model.TypeDetails;
import org.hl7.fhir.r4b.utils.FHIRPathEngine.IEvaluationContext;
import org.hl7.fhir.r4b.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.r4b.utils.structuremap.ITransformerServices;
import org.hl7.fhir.r4b.utils.FHIRPathUtilityClasses.FunctionDetails;


import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.PathEngineException;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;

public class EvaluatorHAPI {

  public EvaluatorHAPI(FhirContext context, IWorkerContext workerContext) {
    _ctx = context;
    _workerContext = workerContext;
  }

  private FhirContext _ctx;
  private IWorkerContext _workerContext;

  @Operation(name = "fhirpath", idempotent = true, returnParameters = {
      @OperationParam(name = "resource", min = 1),
      @OperationParam(name = "expressions", typeName = "string", min = 1)
  })
  public IBaseParameters evaluate(HttpServletRequest theServletRequest,

      @OperationParam(name = "resource", min = 1) IBaseResource resource,
      @OperationParam(name = "context") String contextExpression,
      @OperationParam(name = "expression") String expression,
      @OperationParam(name = "variables") Parameters.ParametersParameterComponent variables) {

    IBaseParameters responseParameters = ParametersUtil.newInstance(_ctx);
    responseParameters.setId("fhirpath");

    if (isNotBlank(expression)) {
      // echo the parameters used
      Parameters.ParametersParameterComponent paramsPart = (Parameters.ParametersParameterComponent) ParametersUtil
          .addParameterToParameters(_ctx, responseParameters,
              "parameters");
      ParametersUtil.addPartString(_ctx, paramsPart, "evaluator", FhirService.hapiVersion + " (r4b)");
      if (contextExpression != null)
        ParametersUtil.addPartString(_ctx, paramsPart, "context", contextExpression);
      ParametersUtil.addPartString(_ctx, paramsPart, "expression", expression);
      ParametersUtil.addPartResource(_ctx, paramsPart, "resource", resource);

      IFhirPath fhirPath = _ctx.newFhirPath();
      IParser parser = _ctx.newJsonParser();

      org.hl7.fhir.r4b.utils.FHIRPathEngine engine = new org.hl7.fhir.r4b.utils.FHIRPathEngine(
          _workerContext);
      FHIRPathTestEvaluationServices services = new FHIRPathTestEvaluationServices();
      engine.setHostServices(services);

      // pass through all the variables
      if (variables != null) {
        paramsPart.addPart(variables);
        java.util.List<org.hl7.fhir.r4b.model.Parameters.ParametersParameterComponent> variableParts = variables
            .getPart();
        for (int i = 0; i < variableParts.size(); i++) {
          org.hl7.fhir.r4b.model.Parameters.ParametersParameterComponent part = variableParts.get(i);
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
        org.hl7.fhir.r4b.model.Base node = (org.hl7.fhir.r4b.model.Base) contextOutputs.get(i);
        Parameters.ParametersParameterComponent resultPart = (Parameters.ParametersParameterComponent) ParametersUtil
            .addParameterToParameters(_ctx, responseParameters,
                "result");
        if (contextExpression != null)
          resultPart.setValue(new StringType(String.format("%s[%d]", contextExpression, i)));

        List<org.hl7.fhir.r4b.model.Base> outputs;
        try {
          services.traceToParameter = resultPart;
          outputs = engine.evaluate(node, expression);
        } catch (FhirPathExecutionException e) {
          throw new InvalidRequestException(
              Msg.code(327) + "Error parsing FHIRPath expression: " + e.getMessage());
        }

        for (IBase nextOutput : outputs) {
          if (nextOutput instanceof IBaseResource) {
            ParametersUtil.addPartResource(_ctx, resultPart, nextOutput.fhirType(), (IBaseResource) nextOutput);
          } else if (nextOutput instanceof org.hl7.fhir.r4b.model.BackboneElement) {
            Parameters.ParametersParameterComponent backboneValue = resultPart.addPart();
            backboneValue.setName(nextOutput.fhirType());
            String backboneJson = parser.encodeToString(nextOutput);
            backboneValue.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                new StringType(backboneJson));
          } else {
            try {
              if (nextOutput instanceof StringType) {
                StringType st = (StringType) nextOutput;
                if (st.getValue() == "")
                  ParametersUtil.addPart(_ctx, resultPart, "empty-string", nextOutput);
                else
                  ParametersUtil.addPart(_ctx, resultPart, nextOutput.fhirType(), nextOutput);
              } else {
                ParametersUtil.addPart(_ctx, resultPart, nextOutput.fhirType(), nextOutput);
              }
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

  @Operation(name = "transform", idempotent = true, returnParameters = {
      @OperationParam(name = "outcome", min = 0),
      @OperationParam(name = "result", typeName = "string", min = 0)
  })
  public IBaseParameters transform(HttpServletRequest theServletRequest,
      @OperationParam(name = "resource", min = 0) IBaseResource resource,
      @OperationParam(name = "map") String mapString) {

    IBaseParameters responseParameters = ParametersUtil.newInstance(_ctx);
    responseParameters.setId("map");
    Parameters.ParametersParameterComponent paramsPart = (Parameters.ParametersParameterComponent) ParametersUtil
        .addParameterToParameters(_ctx, responseParameters,
            "parameters");
    ParametersUtil.addPartString(_ctx, paramsPart, "evaluator", FhirService.hapiVersion + " (r4b)");

    List<Base> outputs = new ArrayList<>();
    var tu = new TransformSupportServices(_workerContext, outputs);
    tu.traceToParameter = (Parameters.ParametersParameterComponent) ParametersUtil.addParameterToParameters(_ctx,
        responseParameters, "trace");
    var smu = new StructureMapUtilities(_workerContext, tu);
    var map = smu.parse(mapString, "map");
    ParametersUtil.addPartResource(_ctx, paramsPart, "map", map);

    org.hl7.fhir.r4b.elementmodel.Element target = getTargetResourceFromStructureMap(map);
    smu.transform(null, (org.hl7.fhir.r4b.model.Resource) resource, map, target);

    Writer sw = new java.io.StringWriter();
    var jsonCreator = new org.hl7.fhir.r4b.formats.JsonCreatorDirect(sw);
    var jp = new org.hl7.fhir.r4b.elementmodel.JsonParser(_workerContext);
    try {
      jp.compose(target, jsonCreator);
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    // run this string through the other parser to make it pretty
    var parser = _ctx.newJsonParser().setPrettyPrint(true);
    var outputContent = parser.encodeResourceToString(parser.parseResource(sw.toString()));

    ParametersUtil.addParameterToParametersString(_ctx, responseParameters, "result", outputContent);
    return responseParameters;
  }

  private org.hl7.fhir.r4b.elementmodel.Element getTargetResourceFromStructureMap(StructureMap map) {
    String targetTypeUrl = null;
    for (StructureMap.StructureMapStructureComponent component : map.getStructure()) {
      if (component.getMode() == StructureMap.StructureMapModelMode.TARGET) {
        targetTypeUrl = component.getUrl();
        break;
      }
    }

    // if (targetTypeUrl == null) {
    // log.error("Unable to determine resource URL for target type");
    // throw new FHIRException("Unable to determine resource URL for target type");
    // }

    StructureDefinition structureDefinition = _workerContext.fetchResource(StructureDefinition.class, targetTypeUrl);
    // for (StructureDefinition sd :
    // _ctx.fetchResourcesByType(StructureDefinition.class)) {
    // if (sd.getUrl().equalsIgnoreCase(targetTypeUrl)) {
    // structureDefinition = sd;
    // break;
    // }
    // }

    // if (structureDefinition == null) {
    // log.error("Unable to find StructureDefinition for target type ('" +
    // targetTypeUrl + "')");
    // throw new FHIRException("Unable to find StructureDefinition for target type
    // ('" + targetTypeUrl + "')");
    // }

    return Manager.build(_workerContext, structureDefinition);
  }

  public class TransformSupportServices implements ITransformerServices {

    public Parameters.ParametersParameterComponent traceToParameter;
    private List<Base> outputs;
    private IWorkerContext context;

    public TransformSupportServices(IWorkerContext worker, List<Base> outputs) {
      this.context = worker;
      this.outputs = outputs;
    }

    @Override
    public Base createType(Object appInfo, String name) throws FHIRException {
      StructureDefinition sd = context.fetchResource(StructureDefinition.class, name);
      return Manager.build(context, sd);
    }

    @Override
    public Base createResource(Object appInfo, Base res, boolean atRootofTransform) {
      if (atRootofTransform)
        outputs.add(res);
      return res;
    }

    @Override
    public Coding translate(Object appInfo, Coding source, String conceptMapUrl) throws FHIRException {
      ConceptMapEngine cme = new ConceptMapEngine(context);
      return cme.translate(source, conceptMapUrl);
    }

    @Override
    public Base resolveReference(Object appContext, String url) throws FHIRException {
      org.hl7.fhir.r4b.model.Resource resource = context.fetchResource(org.hl7.fhir.r4b.model.Resource.class, url);
      return resource;
      // if (resource != null) {
      // String inStr =
      // FhirContext.forR4Cached().newJsonParser().encodeResourceToString(resource);
      // try {
      // return Manager.parseSingle(context, new
      // ByteArrayInputStream(inStr.getBytes()), FhirFormat.JSON);
      // } catch (IOException e) {
      // throw new FHIRException("Cannot convert resource to element model");
      // }
      // }
      // throw new FHIRException("resolveReference, url not found: " + url);
    }

    @Override
    public List<Base> performSearch(Object appContext, String url) throws FHIRException {
      throw new FHIRException("performSearch is not supported yet");
    }

    @Override
    public void log(String message) {
      if (traceToParameter != null) {
        Parameters.ParametersParameterComponent traceValue = traceToParameter.addPart();
        traceValue.setName("debug");
        traceValue.setValue(new StringType(message));
      }
    }
  }

  private class FHIRPathTestEvaluationServices implements IEvaluationContext {
    public Parameters.ParametersParameterComponent traceToParameter;
    public java.util.HashMap<String, org.hl7.fhir.r4b.model.Base> mapVariables;

    public FHIRPathTestEvaluationServices() {
      mapVariables = new HashMap<String, org.hl7.fhir.r4b.model.Base>();
    }

    @Override
    public List<org.hl7.fhir.r4b.model.Base> resolveConstant(Object appContext, String name, boolean beforeContext)
        throws PathEngineException {
      if (mapVariables != null) {
        if (mapVariables.containsKey(name)) {
          List<org.hl7.fhir.r4b.model.Base> result = new java.util.ArrayList<org.hl7.fhir.r4b.model.Base>();
          org.hl7.fhir.r4b.model.Base itemValue = mapVariables.get(name);
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
          "Not done yet (FHIRPathTestEvaluationServices.resolveConstantType), when item is element: " + name);
    }

    @Override
    public boolean log(String argument, List<org.hl7.fhir.r4b.model.Base> data) {
      if (traceToParameter != null) {
        Parameters.ParametersParameterComponent traceValue = traceToParameter.addPart();
        traceValue.setName("trace");
        traceValue.setValue(new StringType(argument));
        IParser parser = _ctx.newJsonParser();

        for (IBase nextOutput : data) {
          if (nextOutput instanceof IBaseResource) {
            ParametersUtil.addPartResource(_ctx, traceValue, nextOutput.fhirType(), (IBaseResource) nextOutput);
          } else if (nextOutput instanceof org.hl7.fhir.r4b.model.BackboneElement) {
            Parameters.ParametersParameterComponent backboneValue = traceValue.addPart();
            backboneValue.setName(nextOutput.fhirType());
            String backboneJson = parser.encodeToString(nextOutput);
            backboneValue.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                new StringType(backboneJson));
          } else {
            // if ( netOutput instanceOf org.hl7.fhir.r4b.model.BackboneElement)
            try {
              if (nextOutput instanceof StringType) {
                StringType st = (StringType) nextOutput;
                if (st.getValue() == "")
                  ParametersUtil.addPart(_ctx, traceValue, "empty-string", nextOutput);
                else
                  ParametersUtil.addPart(_ctx, traceValue, nextOutput.fhirType(), nextOutput);
              } else {
                ParametersUtil.addPart(_ctx, traceValue, nextOutput.fhirType(), nextOutput);
              }
              // ParametersUtil.addPart(ctx, traceValue, nextOutput.fhirType(), nextOutput);
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
    public List<org.hl7.fhir.r4b.model.Base> executeFunction(Object appContext, List<org.hl7.fhir.r4b.model.Base> focus,
        String functionName, List<List<org.hl7.fhir.r4b.model.Base>> parameters) {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices.executeFunction), when item is element: " + functionName);
    }

    @Override
    public org.hl7.fhir.r4b.model.Base resolveReference(Object appContext, String url,
        org.hl7.fhir.r4b.model.Base refContext) throws FHIRException {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices.resolveReference), when item is element");
    }

    @Override
    public boolean conformsToProfile(Object appContext, org.hl7.fhir.r4b.model.Base item, String url)
        throws FHIRException {
      // if (url.equals("http://hl7.org/fhir/StructureDefinition/Patient"))
      // return true;
      // if (url.equals("http://hl7.org/fhir/StructureDefinition/Person"))
      // return false;
      throw new FHIRException("unknown profile " + url);
    }

    @Override
    public org.hl7.fhir.r4b.model.ValueSet resolveValueSet(Object appContext, String url) {
      throw new NotImplementedException(
          "Not done yet (FHIRPathTestEvaluationServices.resolveReference), when item is element");
    }
  }
}
