package com.ibs;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.*;
import java.util.*;
import java.math.BigDecimal;
@SpringBootApplication
@RestController
@RequestMapping("/api")
public class BankingApplication {
 public static void main(String[] args){SpringApplication.run(BankingApplication.class,args);}
 static class Demo {
 BigDecimal balance=new BigDecimal("25000.00");
 Set<String> payees=new LinkedHashSet<>();
 List<Map<String,Object>> transactions=new ArrayList<>();
 }
 Demo demo(HttpServletRequest r){HttpSession s=r.getSession(false); if(s==null||s.getAttribute("demo")==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Login required");return (Demo)s.getAttribute("demo");}
 void check(HttpServletRequest r){if(!"IBS-DEMO".equals(r.getHeader("X-IBS-Client")))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP","database","DEFERRED","application","IBS Banking");}
 @PostMapping("/login") public Map<String,String> login(@RequestBody Map<String,String> b,HttpServletRequest r){check(r);if(!"demo".equals(b.get("username"))||!"IBSdemo123".equals(b.get("password")))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid demo credentials");HttpSession old=r.getSession(false);if(old!=null)old.invalidate();r.getSession(true).setAttribute("demo",new Demo());return Map.of("customer","Demo Customer");}
 @PostMapping("/logout") public Map<String,String> logout(HttpServletRequest r){check(r);HttpSession s=r.getSession(false);if(s!=null)s.invalidate();return Map.of("status","LOGGED_OUT");}
 @GetMapping("/accounts") public List<Map<String,Object>> accounts(HttpServletRequest r){Demo d=demo(r);synchronized(d){return List.of(Map.of("account","DEMO001","balance",d.balance,"currency","INR"));}}
 @GetMapping("/beneficiaries") public List<String> beneficiaries(HttpServletRequest r){Demo d=demo(r);synchronized(d){return List.copyOf(d.payees);}}
 @PostMapping("/beneficiaries") public Map<String,String> add(@RequestBody Map<String,String> b,HttpServletRequest r){check(r);Demo d=demo(r);String a=b.get("account");if(a==null||!a.matches("DEMO[0-9]{3}")||a.equals("DEMO001"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use another DEMO account");synchronized(d){if(d.payees.size()>=50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payee limit");if(!d.payees.add(a))throw new ResponseStatusException(HttpStatus.CONFLICT,"Duplicate payee");}return Map.of("account",a);}
 @PostMapping("/transfer") public Map<String,Object> transfer(@RequestBody Map<String,String> b,HttpServletRequest r){check(r);Demo d=demo(r);BigDecimal amount;try{String raw=b.get("amount");if(raw==null||!raw.matches("[0-9]{1,6}(\\.[0-9]{1,2})?"))throw new IllegalArgumentException();amount=new BigDecimal(raw);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid amount");}synchronized(d){if(amount.signum()<=0||!d.payees.contains(b.get("account")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid transfer");if(d.transactions.size()>=200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Demo transaction limit");String status=amount.compareTo(d.balance)>0?"FAILED":"SUCCESS";if(status.equals("SUCCESS"))d.balance=d.balance.subtract(amount);Map<String,Object> t=Map.of("reference",UUID.randomUUID().toString(),"account",b.get("account"),"amount",amount,"status",status);d.transactions.add(t);return t;}}
 @GetMapping("/transactions") public List<Map<String,Object>> transactions(HttpServletRequest r){Demo d=demo(r);synchronized(d){return List.copyOf(d.transactions);}}
}
