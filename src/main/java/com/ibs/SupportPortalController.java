package com.ibs;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
public class SupportPortalController {
 static boolean authorized(HttpServletRequest req){var s=req.getSession(false);return s!=null&&s.getAttribute("supportUntil") instanceof Long until&&until>System.currentTimeMillis();}
 @PostMapping("/api/support/login") public Map<String,Boolean> login(@RequestHeader(value="X-Support-Key",required=false) String key,HttpServletRequest req){
 String secret=System.getenv("IBS_SUPPORT_KEY");
 if(secret==null||secret.isBlank()||key==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
 var s=req.getSession(true);req.changeSessionId();s.setAttribute("supportUntil",System.currentTimeMillis()+1800000L);return Map.of("authenticated",true);
 }
 @PostMapping("/api/support/logout") public Map<String,Boolean> logout(HttpServletRequest req){var s=req.getSession(false);if(s!=null)s.removeAttribute("supportUntil");return Map.of("authenticated",false);}
 @GetMapping(value="/support-dashboard",produces="text/html") public ResponseEntity<String> dashboard(HttpServletRequest req){
 if(!authorized(req))return ResponseEntity.status(302).header("Location","/support-login.html").build();
 return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body("""
 <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>IBS Production Support</title><style>body{font:16px system-ui;background:#edf3fa;color:#17314e;max-width:850px;margin:auto;padding:20px}a,button{display:block;padding:16px;margin:12px 0;background:white;border-radius:10px;color:#1559a2;border:1px solid #aab9ca;text-decoration:none}small{display:block;color:#456}</style><h1>IBS Production Support Dashboard</h1><p>Authenticated support session · 30 minutes</p><a href="/support.html">Health Monitoring<small>CPU, memory, JVM, threads and HTTP health</small></a><a href="/logs.html">Log Analysis<small>Search request IDs, status and errors</small></a><a href="/web-health">Web Server Health</a><a href="/api/health">Application Health</a><a href="/actuator/health">Spring Boot Health</a><a href="/api/runtime-health">Runtime Metrics API</a><a href="/">Customer Banking Portal</a><button id="logout">Support Logout</button><script>document.getElementById('logout').onclick=async()=>{const r=await fetch('/api/support/logout',{method:'POST'});if(r.ok)location.replace('/support-login.html');else alert('Logout failed; retry');};</script></html>
 """);}
}
