package com.mdm.web;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;

@Controller
public class WorkController {
    private final WorkService service;
    private final ReviewerAccess reviewer;
    public WorkController(WorkService service,ReviewerAccess reviewer) { this.service=service;this.reviewer=reviewer; }
    @GetMapping("/login") String login(jakarta.servlet.http.HttpServletRequest request,Model model) {
        request.getSession().setAttribute("mdm.login.issued",System.currentTimeMillis());
        model.addAttribute("reviewer",reviewer.view());return "login";
    }
    @GetMapping("/") String home(Principal p,Model m,@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String priority,
            @RequestParam(defaultValue="0") long team,@RequestParam(defaultValue="0") int page) {
        var a=service.actor(p.getName());var rows=service.list(a,q,status,priority,team,page);
        m.addAttribute("actor",a);m.addAttribute("teams",service.teams(a));m.addAttribute("hasNext",rows.size()>20);
        m.addAttribute("items",rows.stream().limit(20).toList());m.addAttribute("q",q);m.addAttribute("status",status);
        m.addAttribute("priority",priority);m.addAttribute("team",team);m.addAttribute("page",Math.max(0,Math.min(page,10000)));
        m.addAttribute("summary",service.summary(a));m.addAttribute("createToken",java.util.UUID.randomUUID().toString());
        return "board";
    }
    @GetMapping("/work/{id}") String detail(Principal p,@PathVariable long id,Model m,@RequestParam(defaultValue="9223372036854775807") long before) {
        var a=service.actor(p.getName());var item=service.item(a,id);m.addAttribute("actor",a);m.addAttribute("item",item);
        m.addAttribute("states",service.nextStates(item,a));m.addAttribute("candidates",service.candidates(a,id));m.addAttribute("commentToken",java.util.UUID.randomUUID().toString());
        m.addAttribute("history",service.history(a,id,before));return "detail";
    }
    @PostMapping("/work") String create(Principal p,@RequestParam String title,@RequestParam String description,
            @RequestParam String priority,@RequestParam long team,@RequestParam String next,@RequestParam String token) {
        return "redirect:/work/"+service.create(service.actor(p.getName()),title,description,priority,team,next,token);
    }
    @PostMapping("/work/{id}/claim") String claim(Principal p,@PathVariable long id,@RequestParam int version) {
        service.claim(service.actor(p.getName()),id,version);return "redirect:/work/"+id;
    }
    @PostMapping("/work/{id}/update") String update(Principal p,@PathVariable long id,@RequestParam int version,
            @RequestParam String status,@RequestParam String priority,@RequestParam String next) {
        service.update(service.actor(p.getName()),id,version,status,priority,next);return "redirect:/work/"+id;
    }
    @PostMapping("/work/{id}/comments") String comment(Principal p,@PathVariable long id,@RequestParam String body,@RequestParam String token) {
        service.comment(service.actor(p.getName()),id,body,token);return "redirect:/work/"+id;
    }
    @PostMapping("/work/{id}/reassign") String reassign(Principal p,@PathVariable long id,@RequestParam int version,@RequestParam long owner,@RequestParam String reason) {
        service.reassign(service.actor(p.getName()),id,version,owner,reason);return "redirect:/work/"+id;
    }
    @ExceptionHandler(ResponseStatusException.class) String failure(ResponseStatusException e,Model m,HttpServletResponse response) {
        response.setStatus(e.getStatusCode().value());m.addAttribute("message",e.getReason());return "problem";
    }
    @ExceptionHandler(DataAccessException.class) String databaseFailure(Model m,HttpServletResponse response) {
        response.setStatus(503);m.addAttribute("message","The database is unavailable or setup is incomplete. Check the server log and database setup, then retry.");return "problem";
    }
}
