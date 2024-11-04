package com.kinder.kindergarten.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

  @RequestMapping("/error")
  public String handleError(HttpServletRequest request, Model model) {
    Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    Object error = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
    Object message = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

    model.addAttribute("status", status);
    model.addAttribute("error", error);
    model.addAttribute("message", message);

    return "error/error";
  }
}