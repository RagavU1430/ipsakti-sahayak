package com.ipsakti.ip_sakti_backend.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/",
            "/ask",
            "/formulations",
            "/regulatory",
            "/history",
            "/history/**",
            "/login",
            "/account",
            "/about"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
