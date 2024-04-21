package org.example;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Service;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

/* This simple service is included just to return valid content for the system health tracker */
@Service
public class HomeService extends jakarta.servlet.http.HttpServlet {

  public HomeService() {
    super();
  }

  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    res.setContentType("text/html");
    PrintWriter out = res.getWriter();
    out.println("<HTML><HEAD><TITLE>FHIRPath Lab JAVA</TITLE></HEAD>");
    out.println("<body>");
    out.println("<div>");
    out.println("FHIRPath Lab JAVA Engine executor: ");
    out.println(req.getRequestURI());
    out.println("</div>");
    out.println("</body>");
    out.println("</HTML>");
    out.close();
  }

  @Bean
  public ServletRegistrationBean<HomeService> homeServlet() {
    return new ServletRegistrationBean<HomeService>(
        new HomeService(), "/");
  }
}