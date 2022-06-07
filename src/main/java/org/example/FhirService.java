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
    super(FhirContext.forR4());

    registerProvider(new Evaluator());
  }

  @Override
  protected void initialize() throws ServletException {
    super.initialize();
  }

  @Bean
  public ServletRegistrationBean fhirServlet() {
    return new ServletRegistrationBean(
        new FhirService(), "/fhir/*");
  }
}