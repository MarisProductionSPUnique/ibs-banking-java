package com.ibs;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
 private static final Logger log=LoggerFactory.getLogger(RequestLoggingFilter.class);
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
 String id=UUID.randomUUID().toString();
 long start=System.nanoTime();
 res.setHeader("X-Request-ID",id);
 MDC.put("requestId",id);
 boolean failed=false;
 try {chain.doFilter(req,res);} catch(ServletException|IOException|RuntimeException e){failed=true;log.error("requestId={} exception={}",id,e.getClass().getSimpleName());throw e;}
 finally {
 int status=failed?500:res.getStatus();
 String raw=req.getServletPath();
 String path=java.util.Set.of("/api/login","/api/logout","/api/accounts","/api/beneficiaries","/api/transfer","/api/transactions","/api/health").contains(raw)?raw:"OTHER";
 long ms=(System.nanoTime()-start)/1000000;
 if(status>=500)log.error("requestId={} method={} path={} status={} durationMs={}",id,req.getMethod(),path,status,ms);
 else if(status>=400)log.warn("requestId={} method={} path={} status={} durationMs={}",id,req.getMethod(),path,status,ms);
 else log.info("requestId={} method={} path={} status={} durationMs={}",id,req.getMethod(),path,status,ms);
 if(!raw.startsWith("/api/support") && raw.startsWith("/api/")) SupportLogController.record(id,req.getMethod(),path,status,ms);
 MDC.remove("requestId");
 }
 }
}
