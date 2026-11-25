package com.ibs;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/support")
public class SupportLogController {
 private static final Deque<Map<String,Object>> events=new ArrayDeque<>();
 static synchronized void record(String id,String method,String path,int status,long ms){
 if(path.startsWith("/api/support"))return;
 events.addFirst(Map.of("time",Instant.now().toString(),"requestId",id,"method",method,"path",path,"status",status,"durationMs",ms,"level",status>=500?"ERROR":status>=400?"WARN":"INFO"));
 while(events.size()>500)events.removeLast();
 }
 @GetMapping("/logs") public List<Map<String,Object>> logs(jakarta.servlet.http.HttpServletRequest req,@RequestHeader(value="X-Support-Key",required=false) String key,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String level,@RequestParam(defaultValue="") String status){
 String secret=System.getenv("IBS_SUPPORT_KEY");
 if(!SupportPortalController.authorized(req)&&(secret==null||secret.isBlank()||key==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8))))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
 synchronized(SupportLogController.class){return events.stream().filter(e->level.isBlank()||level.equals(e.get("level"))).filter(e->status.isBlank()||status.equals(e.get("status").toString())).filter(e->q.isBlank()||e.toString().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))).toList();}
 }
}
