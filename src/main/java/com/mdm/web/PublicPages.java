package com.mdm.web;

import java.time.LocalDate;
import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

@Controller
public class PublicPages {
    private final String baseUrl,operator,contact;private final boolean analytics;private final JdbcTemplate jdbc;
    public PublicPages(JdbcTemplate jdbc,@Value("${mdm.site.base-url:http://localhost:8080}") String baseUrl,
        @Value("${mdm.legal.operator:Your workspace operator}") String operator,@Value("${mdm.legal.contact:}") String contact,
        @Value("${mdm.analytics.enabled:false}") boolean analytics){this.jdbc=jdbc;this.baseUrl=baseUrl.replaceAll("/+$","");this.operator=operator;this.contact=contact;this.analytics=analytics;}
    @GetMapping("/welcome") String welcome(){return "welcome";}
    @GetMapping("/privacy") String privacy(Model m){m.addAttribute("operator",operator);m.addAttribute("contact",contact);return "privacy";}
    @GetMapping("/terms") String terms(Model m){m.addAttribute("operator",operator);m.addAttribute("contact",contact);return "terms";}
    @GetMapping(value="/robots.txt",produces=MediaType.TEXT_PLAIN_VALUE) @ResponseBody String robots(){return "User-agent: *\nDisallow: /\nAllow: /welcome$\nAllow: /privacy$\nAllow: /terms$\nSitemap: "+baseUrl+"/sitemap.xml\n";}
    @GetMapping(value="/sitemap.xml",produces=MediaType.APPLICATION_XML_VALUE) @ResponseBody String sitemap(){
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"+
            java.util.stream.Stream.of("/welcome","/privacy","/terms").map(p->"<url><loc>"+org.springframework.web.util.HtmlUtils.htmlEscape(baseUrl+p)+"</loc></url>").collect(java.util.stream.Collectors.joining())+"</urlset>";
    }
    public record PageView(@NotNull @Pattern(regexp="/(welcome|privacy|terms)") String page){}
    @PostMapping("/analytics/pageview") @ResponseBody ResponseEntity<Void> analytics(@Valid @RequestBody PageView view,@RequestHeader(value="X-MDM-Consent",defaultValue="") String consent){
        if(!analytics)return ResponseEntity.noContent().build();
        if(!"analytics-v1".equals(consent))return ResponseEntity.badRequest().build();
        jdbc.update("INSERT INTO analytics_daily(page,day,views) VALUES (?,CURRENT_DATE,1) ON DUPLICATE KEY UPDATE views=views+1",view.page());
        return ResponseEntity.noContent().build();
    }
}
