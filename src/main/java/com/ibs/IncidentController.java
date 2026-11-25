package com.ibs;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
public class IncidentController {
 private static final Logger log=LoggerFactory.getLogger(IncidentController.class);
 private static final Map<String,Integer> codes=Map.ofEntries(Map.entry("400",400),Map.entry("401",401),Map.entry("403",403),Map.entry("404",404),Map.entry("500",500),Map.entry("502",502),Map.entry("503",503),Map.entry("504",504),Map.entry("NPE",500),Map.entry("SQL",500),Map.entry("TIMEOUT",504),Map.entry("CPU",503),Map.entry("MEMORY",503),Map.entry("DISK",503),Map.entry("HEAP",503),Map.entry("DEADLOCK",503),Map.entry("POOL",503),Map.entry("BATCH",503),Map.entry("MISSING",503),Map.entry("PERMISSION",503),Map.entry("API",504));
 @GetMapping("/api/training/validate") public Map<String,Boolean> validate(@RequestHeader(value="X-Support-Key",required=false) String key){String secret=System.getenv("IBS_SUPPORT_KEY");if(secret==null||secret.isBlank()||key==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return Map.of("authenticated",true);}
 @PostMapping("/api/training/incident/{scenario}")
 public ResponseEntity<Map<String,Object>> simulate(@RequestHeader(value="X-Support-Key",required=false) String key,@PathVariable String scenario){
 String secret=System.getenv("IBS_SUPPORT_KEY");
 if(secret==null||secret.isBlank()||key==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
 Integer status=codes.get(scenario);
 if(status==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
 String detail=switch(scenario){case "NPE"->"NullPointerException: simulated missing account object";case "SQL"->"SQLException: simulated database connection failure";case "TIMEOUT"->"SocketTimeoutException: simulated downstream timeout";case "CPU"->"Simulated CPU utilization 95%; Java worker busy";case "MEMORY"->"Simulated container memory 94%; allocation pressure";case "DISK"->"Simulated filesystem 100%; No space left on device";case "HEAP"->"Simulated OutOfMemoryError: Java heap space";case "DEADLOCK"->"Simulated Java thread deadlock";case "POOL"->"Simulated connection pool exhausted";case "BATCH"->"Simulated batch job failed; exit code 1";case "MISSING"->"Simulated inbound file not found";case "PERMISSION"->"Simulated Permission denied";case "API"->"Simulated external API timeout";default->"Simulated HTTP "+status;};
 String id=Objects.toString(MDC.get("requestId"),"unavailable");
 if(status>=500)log.error("TRAINING_SIMULATION requestId={} scenario={} detail={}",id,scenario,detail);else log.warn("TRAINING_SIMULATION requestId={} scenario={} detail={}",id,scenario,detail);
 return ResponseEntity.status(status).body(Map.of("simulation",true,"scenario",scenario,"status",status,"requestId",id,"message",detail));
 }
}
