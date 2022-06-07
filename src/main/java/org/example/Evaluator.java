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
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.hapi.ctx.HapiWorkerContext;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.utils.FHIRPathEngine;

public class Evaluator {

  private FhirContext ctx = FhirContext.forR4();

  @Operation(name = "evaluate", idempotent = true, returnParameters = {
      @OperationParam(name = "resource", min = 1),
      @OperationParam(name = "expressions", typeName = "string", min = 1)
  })
  public IBaseParameters evaluate(HttpServletRequest theServletRequest,

      @OperationParam(name = "resource", min = 1) IBaseResource resource,
      @OperationParam(name = "expression") List<String> fhirPathParams) {

    IBaseParameters responseParameters = ParametersUtil.newInstance(ctx);

    for (String expression : fhirPathParams) {

      if (isNotBlank(expression)) {
        IBase resultPart = ParametersUtil.addParameterToParameters(ctx, responseParameters,
            "result");
        ParametersUtil.addPartString(ctx, resultPart, "expression", expression);

        IFhirPath fhirPath = ctx.newFhirPath();
        List<IBase> outputs;
        try {
          outputs = fhirPath.evaluate(resource, expression, IBase.class);
        } catch (FhirPathExecutionException e) {
          throw new InvalidRequestException(
              Msg.code(327) + "Error parsing FHIRPath expression: " + e.getMessage());
        }

        for (IBase nextOutput : outputs) {
          if (nextOutput instanceof IBaseResource) {
            ParametersUtil.addPartResource(ctx, resultPart, "result", (IBaseResource) nextOutput);
          } else {
            ParametersUtil.addPart(ctx, resultPart, "result", nextOutput);
          }
        }
      }
    }
    return responseParameters;
  }
}
