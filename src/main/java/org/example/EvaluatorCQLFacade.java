package org.example;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.util.ParametersUtil;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.StringType;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;

public class EvaluatorCQLFacade {

  public EvaluatorCQLFacade(FhirContext context) {
    _ctx = context;
  }

  private FhirContext _ctx;

  @Operation(name = "fhirpath-cql", idempotent = true, returnParameters = {
      @OperationParam(name = "resource", min = 1),
      @OperationParam(name = "expressions", typeName = "string", min = 1)
  })
  public IBaseParameters evaluate(HttpServletRequest theServletRequest,

      @OperationParam(name = "expression") String expression,
      @OperationParam(name = "resource", min = 1) IBaseResource resource,
      @OperationParam(name = "cql-server") String cqlServerUrl) {

    IBaseParameters responseParameters = ParametersUtil.newInstance(_ctx);
    responseParameters.setId("fhirpath");

    if (isNotBlank(expression)) {
      // echo the parameters used
      Parameters.ParametersParameterComponent paramsPart = (Parameters.ParametersParameterComponent) ParametersUtil
          .addParameterToParameters(_ctx, responseParameters,
              "parameters");
      ParametersUtil.addPartString(_ctx, paramsPart, "evaluator", "CQL-8.2 (r4)");
      ParametersUtil.addPartString(_ctx, paramsPart, "expression", expression);
      ParametersUtil.addPartResource(_ctx, paramsPart, "resource", resource);

      IParser parser = _ctx.newJsonParser();

      // Call the specified server's $cql operation with the parameters:
      // * `expression`
      // * `subject` which is constructed from the resource type and id e.g.
      // Patient/123
      // * `data` which will be a bundle that we put our test resource into (unless
      // the resource is a bundle)

      org.hl7.fhir.r4b.model.Parameters cqlParams = (org.hl7.fhir.r4b.model.Parameters) ParametersUtil
          .newInstance(_ctx);
      cqlParams.addParameter("expression", expression);
      cqlParams.addParameter("subject", resource.fhirType() + "/" + resource.getIdElement().getIdPart());
      var pResource = cqlParams.addParameter();
      pResource.setName("data");
      org.hl7.fhir.r4b.model.Bundle dataBundle = new org.hl7.fhir.r4b.model.Bundle();
      dataBundle.setType(org.hl7.fhir.r4b.model.Bundle.BundleType.COLLECTION);
      dataBundle.addEntry().setResource((org.hl7.fhir.r4b.model.Resource) resource);
      pResource.setResource(dataBundle);

      // call out to the Facade server
      String cqlServer = "https://cloud.alphora.com/sandbox/r4/cds/fhir";
      if (isNotBlank(cqlServerUrl))
        cqlServer = cqlServerUrl;
      var cqlClient = _ctx.newRestfulGenericClient(cqlServer);
      org.hl7.fhir.r4b.model.Parameters cqlResponse = cqlClient
          .operation()
          .onServer()
          .named("$cql")
          .withParameters(cqlParams)
          .execute();

      Parameters.ParametersParameterComponent resultPart = (Parameters.ParametersParameterComponent) ParametersUtil
          .addParameterToParameters(_ctx, responseParameters,
              "result");

      List<org.hl7.fhir.r4b.model.Base> outputs = new ArrayList<org.hl7.fhir.r4b.model.Base>();
      // put the results from the CQL parameters into here!
      for (Parameters.ParametersParameterComponent part : cqlResponse.getParameter()) {
        if (part.getResource() != null) {
          outputs.add(part.getResource());
        } else {
          outputs.add(part.getValue());
        }
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
    return responseParameters;
  }
}
