package org.example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.rest.server.RestfulServer;

import java.util.Arrays;

import javax.servlet.ServletException;

import org.hl7.fhir.r4b.context.IWorkerContext;
import org.hl7.fhir.r4b.hapi.ctx.HapiWorkerContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;


@Service
public class FhirService extends RestfulServer {

  static final String hapiVersion = "HAPI-6.10.2";
  
  public FhirService() {
    super(FhirContext.forR4B());
    IWorkerContext workerContext = new HapiWorkerContext(this.getFhirContext(), new DefaultProfileValidationSupport(this.getFhirContext()));
    registerProvider(new EvaluatorHAPI(this.getFhirContext(), workerContext));
    registerProvider(new EvaluatorIBM(this.getFhirContext(), workerContext));
  }

  @Override
  protected void initialize() throws ServletException {
    super.initialize();
  }

  @Bean
  public ServletRegistrationBean<FhirService> fhirServlet() {
    return new ServletRegistrationBean<FhirService>(
        new FhirService(), "/fhir/*");
  }
}