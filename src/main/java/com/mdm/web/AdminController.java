package com.mdm.web;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final WorkService work;
    private final AdminService admin;
    public AdminController(WorkService work,AdminService admin){this.work=work;this.admin=admin;}
    @GetMapping String page(Principal p,Model m,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") long team,@RequestParam(defaultValue="0") int memberPage){
        var a=work.actor(p.getName());admin.requireAdmin(a);var users=admin.users(a,page);var members=admin.members(a,team,memberPage);
        m.addAttribute("actor",a);m.addAttribute("teams",work.teams(a));m.addAttribute("users",users.stream().limit(50).toList());m.addAttribute("hasNext",users.size()>50);
        m.addAttribute("members",members.stream().limit(50).toList());m.addAttribute("hasMoreMembers",members.size()>50);
        m.addAttribute("page",Math.max(0,Math.min(page,10000)));m.addAttribute("memberPage",Math.max(0,Math.min(memberPage,10000)));m.addAttribute("team",team);m.addAttribute("history",admin.history(a));return "admin";
    }
    @PostMapping("/teams") String team(Principal p,@RequestParam String name,RedirectAttributes flash){admin.createTeam(work.actor(p.getName()),name);flash.addFlashAttribute("notice","Team created. Add members below.");return "redirect:/admin";}
    @GetMapping("/analytics") String analytics(Principal p,Model m){var a=work.actor(p.getName());admin.requireAdmin(a);m.addAttribute("rows",admin.analytics(a));return "analytics";}
    @PostMapping("/users") String user(Principal p,@RequestParam String name,@RequestParam String email,@RequestParam String password,RedirectAttributes flash){admin.createUser(work.actor(p.getName()),name,email,password);flash.addFlashAttribute("notice","Member account created. Add it to a team to grant access.");return "redirect:/admin";}
    @PostMapping("/memberships") String membership(Principal p,@RequestParam long user,@RequestParam long team,RedirectAttributes flash){admin.addMember(work.actor(p.getName()),user,team);flash.addFlashAttribute("notice","Team membership added.");return "redirect:/admin?team="+team;}
    @ExceptionHandler(ResponseStatusException.class) String denied(ResponseStatusException e,Model m,HttpServletResponse r){r.setStatus(e.getStatusCode().value());m.addAttribute("message",e.getReason());return "problem";}
    @ExceptionHandler(DuplicateKeyException.class) String duplicate(Model m,HttpServletResponse r){r.setStatus(409);m.addAttribute("message","This name, email or membership already exists. Return to administration and review the existing record.");return "problem";}
    @ExceptionHandler(DataAccessException.class) String database(Model m,HttpServletResponse r){r.setStatus(503);m.addAttribute("message","Database access failed. Check the server log and retry after resolving the problem.");return "problem";}
}
