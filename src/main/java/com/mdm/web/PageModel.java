package com.mdm.web;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
@ControllerAdvice
public class PageModel {
    @Value("${mdm.site.base-url:http://localhost:8080}") String baseUrl;
    @Value("${mdm.analytics.enabled:false}") boolean analytics;
    @Value("${mdm.password-reset.email-enabled:false}") boolean passwordResetEmailEnabled;
    @ModelAttribute void metadata(Model model){model.addAttribute("siteBase",baseUrl.replaceAll("/+$",""));model.addAttribute("analyticsEnabled",analytics);model.addAttribute("passwordResetEmailEnabled",passwordResetEmailEnabled);}
}
