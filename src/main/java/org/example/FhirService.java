package org.example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import javax.servlet.ServletException;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;


@Service
public class FhirService extends RestfulServer {

  public FhirService() {
    super(FhirContext.forR4B());
    registerProvider(new EvaluatorHAPI(this.getFhirContext()));
    registerProvider(new EvaluatorIBM(this.getFhirContext()));
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