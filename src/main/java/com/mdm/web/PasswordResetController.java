package com.mdm.web;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PasswordResetController {
    static final String SESSION_TOKEN="mdm.password-reset.token";
    private final PasswordResetService resets;private final PasswordResetMail mail;
    public PasswordResetController(PasswordResetService resets,PasswordResetMail mail){this.resets=resets;this.mail=mail;}
    @GetMapping("/forgot-password") String forgot(HttpServletRequest req){req.getSession().setAttribute("mdm.forgot.issued",System.currentTimeMillis());return "forgot-password";}
    @PostMapping("/forgot-password") String request(@RequestParam String email,@RequestParam(defaultValue="") String website,HttpServletRequest req,Model model,HttpServletResponse response){
        if(email.length()>150||!email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")){response.setStatus(400);model.addAttribute("formError","Enter a valid email address.");return "forgot-password";}
        var session=req.getSession(false);Object issued=session==null?null:session.getAttribute("mdm.forgot.issued");
        if(website.isBlank()&&issued instanceof Long time&&System.currentTimeMillis()-time>=750&&System.currentTimeMillis()-time<7_200_000)
            mail.request(email.trim().toLowerCase(java.util.Locale.ROOT));
        return "redirect:/forgot-password?sent";
    }
    @GetMapping("/reset-password") String form(@RequestParam(required=false) String token,HttpServletRequest req,Model model){
        if(token!=null){
            if(!resets.usable(token)){req.getSession().removeAttribute(SESSION_TOKEN);model.addAttribute("validLink",false);return "reset-password";}
            req.getSession().setAttribute(SESSION_TOKEN,token);return "redirect:/reset-password";
        }
        var session=req.getSession(false);String saved=session==null?null:(String)session.getAttribute(SESSION_TOKEN);
        model.addAttribute("validLink",resets.usable(saved));return "reset-password";
    }
    @PostMapping("/reset-password") String reset(@RequestParam String password,@RequestParam String confirmation,HttpServletRequest req,Model model,HttpServletResponse response){
        var session=req.getSession(false);String token=session==null?null:(String)session.getAttribute(SESSION_TOKEN);
        try{String email=resets.reset(token,password,confirmation);if(session!=null)session.invalidate();org.springframework.security.core.context.SecurityContextHolder.clearContext();mail.changed(email);return "redirect:/login?reset";}
        catch(ResponseStatusException error){response.setStatus(400);boolean expired=error.getStatusCode().value()==410;if(expired&&session!=null)session.removeAttribute(SESSION_TOKEN);model.addAttribute("validLink",!expired);model.addAttribute("formError",error.getReason());return "reset-password";}
    }
}
