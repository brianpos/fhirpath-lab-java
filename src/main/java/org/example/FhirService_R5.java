package org.example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import javax.servlet.ServletException;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;


@Service
public class FhirService_R5 extends RestfulServer {

  public FhirService_R5() {
    super(FhirContext.forR5());

    registerProvider(new EvaluatorHAPI_R5(this.getFhirContext()));
  }

  @Override
  protected void initialize() throws ServletException {
    super.initialize();
  }

  @Bean
  public ServletRegistrationBean<FhirService_R5> fhirServletR5() {
    return new ServletRegistrationBean<FhirService_R5>(
        new FhirService_R5(), "/fhir5/*");
  }
}