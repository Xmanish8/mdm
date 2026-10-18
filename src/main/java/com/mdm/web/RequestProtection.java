package com.mdm.web;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

public final class RequestProtection {
    private RequestProtection(){}
    public static class Headers extends OncePerRequestFilter {
        private final String origin;private final boolean production;
        public Headers(String baseUrl,boolean production){this.origin=URI.create(baseUrl).getScheme()+"://"+URI.create(baseUrl).getAuthority();this.production=production;}
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
            res.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"+(production?"; upgrade-insecure-requests":""));
            res.setHeader("Referrer-Policy","no-referrer");res.setHeader("X-Content-Type-Options","nosniff");
            res.setHeader("Permissions-Policy","camera=(), microphone=(), geolocation=(), payment=(), usb=()");
            res.setHeader("X-Frame-Options","DENY");
            if(req.isSecure())res.setHeader("Strict-Transport-Security","max-age=31536000");
            String path=req.getRequestURI().substring(req.getContextPath().length());
            boolean known=Set.of("/","/login","/logout","/forgot-password","/reset-password","/welcome","/privacy","/terms","/robots.txt","/sitemap.xml","/analytics/pageview","/style.css","/error","/work","/admin","/admin/analytics","/admin/teams","/admin/users","/admin/memberships").contains(path)
                ||path.startsWith("/assets/")||path.matches("/work/[0-9]+(?:/(?:claim|update|comments|reassign))?");
            if(!known){res.sendError(404);return;}
            if(!Set.of("/welcome","/privacy","/terms","/sitemap.xml","/robots.txt").contains(path))res.setHeader("X-Robots-Tag","noindex, nofollow");
            String incoming=req.getHeader("Origin");
            if(incoming!=null&&!incoming.equals(origin)){
                if(path.equals("/login"))org.slf4j.LoggerFactory.getLogger(Headers.class).info("Login form rejected: site address mismatch. Open the sign-in page at {}",origin+"/login");
                res.sendError(403);return;
            }
            if(req.getContentLengthLong()>1_048_576){res.sendError(413);return;}
            String content=req.getContentType();
            if(content!=null&&content.toLowerCase(Locale.ROOT).startsWith("multipart/")){res.sendError(415);return;}
            if(Set.of("/signup","/register","/upload","/uploads").contains(path)){res.sendError(404);return;}
            chain.doFilter(req,res);
        }
    }
    public static class Throttle extends OncePerRequestFilter {
        private final RateLimits limits;
        public Throttle(RateLimits limits){this.limits=limits;}
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
            String ip=req.getRemoteAddr();String path=req.getRequestURI().substring(req.getContextPath().length());
            if(req.getMethod().equals("GET")&&path.equals("/reset-password")){
                try{if(!limits.allow("reset-link-ip",ip,60,900)){blocked(res);return;}}
                catch(org.springframework.dao.DataAccessException|IllegalStateException error){res.sendError(503);return;}
            }
            if(!Set.of("POST","PUT","PATCH","DELETE").contains(req.getMethod())){chain.doFilter(req,res);return;}
            if(path.equals("/logout")){chain.doFilter(req,res);return;}
            try {
                if(path.equals("/forgot-password")){
                    String email=java.util.Objects.toString(req.getParameter("email"),"");
                    if(email.length()>150){res.sendError(400);return;}
                    if(!limits.allow("forgot-ip",ip,5,900)){blocked(res);return;}
                    if(!limits.allow("forgot-email",email.trim().toLowerCase(Locale.ROOT),3,3600)){res.setHeader("Retry-After","3600");res.sendError(429);return;}
                }else if(path.equals("/reset-password")){
                    if(!limits.allow("reset-submit-ip",ip,10,900)){blocked(res);return;}
                }else if(path.equals("/login")){
                    if(!limits.allow("login-ip",ip,30,900)){blocked(res);return;}
                    String username=req.getParameter("username"),password=req.getParameter("password");
                    if(username==null||username.length()>150||password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72){res.sendError(400);return;}
                    if(!limits.allow("login-account",ip+":"+username.trim().toLowerCase(Locale.ROOT),8,900)){blocked(res);return;}
                    if(!limits.allow("login-account-global",username.trim().toLowerCase(Locale.ROOT),30,900)){blocked(res);return;}
                    var session=req.getSession(false);Object issued=session==null?null:session.getAttribute("mdm.login.issued");
                    if(issued==null||!(issued instanceof Long)||System.currentTimeMillis()-(Long)issued<750||System.currentTimeMillis()-(Long)issued>7_200_000||!java.util.Objects.toString(req.getParameter("website"),"").isBlank()){res.sendError(400);return;}
                } else {
                    var auth=SecurityContextHolder.getContext().getAuthentication();
                    String identity=auth!=null&&!(auth instanceof AnonymousAuthenticationToken)?auth.getName():"anonymous";
                    if(!limits.allow("write",ip+":"+identity,120,60)){blocked(res);return;}
                }
            }catch(org.springframework.dao.DataAccessException|IllegalStateException e){res.sendError(503);return;}
            chain.doFilter(req,res);
        }
        private void blocked(HttpServletResponse res)throws IOException{res.setHeader("Retry-After","900");res.sendError(429);}
    }
}
