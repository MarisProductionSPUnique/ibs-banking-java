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
 private static final Instant metricsSince=Instant.now();
 private static final Map<String,Stats> metrics=new LinkedHashMap<>();
 private static final Set<String> ROUTES=Set.of("/api/login","/api/logout","/api/accounts","/api/beneficiaries","/api/transfer","/api/transactions","/api/health");
 private static final class Stats {
  long total,success,redirect,clientError,serverError,other,slow,sum,max;
  void add(int status,long ms){total++;if(status>=200&&status<300)success++;else if(status>=300&&status<400)redirect++;else if(status>=400&&status<500)clientError++;else if(status>=500&&status<600)serverError++;else other++;if(ms>=2000)slow++;sum+=ms;max=Math.max(max,ms);}
  Map<String,Object> snapshot(){return Map.of("total",total,"success",success,"redirect",redirect,"clientError",clientError,"serverError",serverError,"other",other,"slow",slow,"averageMs",total==0?0.0:(double)sum/total,"maxMs",max,"successRate",total==0?0.0:100.0*success/total);}
 }
 private static final Stats overall=new Stats();
 @GetMapping("/api-metrics") public org.springframework.http.ResponseEntity<Map<String,Object>> apiMetrics(jakarta.servlet.http.HttpServletRequest req){
  if(!SupportPortalController.authorized(req))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  synchronized(SupportLogController.class){
   Map<String,Object> routes=new LinkedHashMap<>();
   metrics.forEach((k,v)->routes.put(k,v.snapshot()));
   return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("since",metricsSince.toString(),"slowThresholdMs",2000,"overall",overall.snapshot(),"routes",routes));
  }
 }
 private static final Deque<Map<String,Object>> events=new ArrayDeque<>();
 static synchronized void record(String id,String method,String path,int status,long ms){
 if(path.startsWith("/api/support"))return;
 String route=ROUTES.contains(path)?path:"OTHER";
 overall.add(status,ms);metrics.computeIfAbsent(route,k->new Stats()).add(status,ms);
 events.addFirst(Map.of("time",Instant.now().toString(),"requestId",id,"method",method,"path",path,"status",status,"durationMs",ms,"level",status>=500?"ERROR":status>=400?"WARN":"INFO"));
 while(events.size()>500)events.removeLast();
 }
 private Instant parseTime(String value){try{return value.isBlank()?null:Instant.parse(value);}catch(java.time.format.DateTimeParseException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use ISO-8601 UTC timestamps");}}
 @GetMapping("/logs") public List<Map<String,Object>> logs(jakarta.servlet.http.HttpServletRequest req,@RequestHeader(value="X-Support-Key",required=false) String key,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String level,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to){
 String secret=System.getenv("IBS_SUPPORT_KEY");
 if(!SupportPortalController.authorized(req)&&(secret==null||secret.isBlank()||key==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8))))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
 Instant start=parseTime(from),end=parseTime(to);
 if(start!=null&&end!=null&&start.isAfter(end))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid time range");
 synchronized(SupportLogController.class){return events.stream().filter(e->{Instant t=Instant.parse(e.get("time").toString());return (start==null||!t.isBefore(start))&&(end==null||!t.isAfter(end));}).filter(e->level.isBlank()||level.equals(e.get("level"))).filter(e->status.isBlank()||status.equals(e.get("status").toString())).filter(e->q.isBlank()||e.toString().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))).toList();}
 }
 @GetMapping(value="/logs/download",produces="text/plain")
 public org.springframework.http.ResponseEntity<String> download(jakarta.servlet.http.HttpServletRequest req,@RequestHeader(value="X-Support-Key",required=false) String key,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String level,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to){
  StringBuilder body=new StringBuilder();
  for(var e:logs(req,key,q,level,status,from,to)){
   body.append(e.get("time")).append(" ").append(e.get("level")).append(" requestId=").append(e.get("requestId")).append(" method=").append(e.get("method")).append(" path=").append(e.get("path")).append(" status=").append(e.get("status")).append(" durationMs=").append(e.get("durationMs")).append("\n");
  }
  return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=ibs-application.log").header("Cache-Control","no-store").body(body.toString());
 }
}
