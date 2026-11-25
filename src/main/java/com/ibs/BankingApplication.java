package com.ibs;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@SpringBootApplication
@RestController
@RequestMapping("/api")
public class BankingApplication {
 public static void main(String[] args){SpringApplication.run(BankingApplication.class,args);}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP","database","DEFERRED","application","IBS Banking");}
 @GetMapping("/accounts") public List<Map<String,Object>> accounts(){return List.of(Map.of("account","DEMO001","balance",25000,"currency","INR"),Map.of("account","DEMO002","balance",15000,"currency","INR"));}
 @GetMapping("/transactions") public List<Map<String,Object>> transactions(){return List.of(Map.of("reference","DEMO-TXN-001","amount",500,"status","SUCCESS"));}
}