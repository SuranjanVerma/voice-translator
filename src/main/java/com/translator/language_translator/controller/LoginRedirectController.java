package com.translator.language_translator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginRedirectController {
    @GetMapping("/login")
    public String loginPage() {
        return "redirect:/login.html";
    }
}