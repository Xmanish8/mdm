package com.mdm.web;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
public class PasswordVersionFilter extends OncePerRequestFilter {
    private final SessionPasswordVersions versions;
    public PasswordVersionFilter(SessionPasswordVersions versions){this.versions=versions;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){String path=request.getRequestURI().substring(request.getContextPath().length());return !(path.equals("/")||path.equals("/work")||path.startsWith("/work/")||path.equals("/admin")||path.startsWith("/admin/"));}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken)){
            boolean valid;try{valid=versions.matches(req.getSession(false),auth.getName());}
            catch(org.springframework.dao.DataAccessException error){res.sendError(503);return;}
            if(!valid){var session=req.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();res.sendRedirect(req.getContextPath()+"/login?expired");return;}
        }
        chain.doFilter(req,res);
    }
}
