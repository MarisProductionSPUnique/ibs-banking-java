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
 void check(HttpServletRequest r){if(!"IBS-DEMO".equals(r.getHeader("X-IBS-Client")))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP","database","DEFERRED","application","IBS Banking");}
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BankingApplication.class);
 private static ResponseStatusException unavailable(String operation, java.sql.SQLException e, HttpServletResponse response){
   String state=e.getSQLState();
   int code=e.getErrorCode();
   String category=e instanceof java.sql.SQLTimeoutException || code==1013 || code==12170 ? "TIMEOUT" :
     code==1017 || code==28000 || code==28001 ? "AUTH_CONFIGURATION" :
     e instanceof java.sql.SQLRecoverableException || (state!=null && state.startsWith("08")) ? "CONNECTIVITY" : "DATABASE_ERROR";
   String incident=UUID.randomUUID().toString();
   LOG.warn("event=ORACLE_UNAVAILABLE operation={} category={} reference={} SQLState={} code={}",operation,category,incident,state,code);
   response.setHeader("X-IBS-Error-Reference",incident);
   response.setHeader("Cache-Control","no-store");
   response.setHeader("Retry-After","30");
   return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Database temporarily unavailable");
 }
 private final OracleAuthentication authentication;
 private final java.util.concurrent.Semaphore loginSlots = new java.util.concurrent.Semaphore(2);
 private long loginWindow = System.nanoTime();
 private int loginAttempts;
 public BankingApplication(OracleAuthentication authentication){this.authentication=authentication;}
 private synchronized boolean allowLogin(){
   long now=System.nanoTime();
   if(now-loginWindow>=60_000_000_000L){loginWindow=now;loginAttempts=0;}
   return ++loginAttempts<=20;
 }
 @PostMapping("/login") public Map<String,String> login(@RequestBody Map<String,String> b,HttpServletRequest r,HttpServletResponse response){
   check(r);
   if(!allowLogin() || !loginSlots.tryAcquire())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Try again later");
   try{
     var customer=authentication.authenticate(b.get("username"),b.get("password"))
       .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials"));
     HttpSession old=r.getSession(false);if(old!=null)old.invalidate();
     HttpSession session=r.getSession(true);
     session.setMaxInactiveInterval(900);
     session.setAttribute("customerId",customer.customerId());
     session.setAttribute("userId",customer.userId());
     
     return Map.of("customer",customer.username(),"bankingMode","ORACLE_READ_ONLY");
   }catch(java.sql.SQLException e){
     throw unavailable("LOGIN",e,response);
   }finally{loginSlots.release();}
 }
 @PostMapping("/logout") public Map<String,String> logout(HttpServletRequest r){check(r);HttpSession s=r.getSession(false);if(s!=null)s.invalidate();return Map.of("status","LOGGED_OUT");}
 @GetMapping("/accounts") public List<Map<String,Object>> accounts(HttpServletRequest r, HttpServletResponse response){
   response.setHeader("Cache-Control","no-store");
   HttpSession session=r.getSession(false);
   if(session==null || !(session.getAttribute("customerId") instanceof Long customerId))
     throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Login required");
   String url=System.getenv("BANK_DB_URL"), user=System.getenv("BANK_DB_USER"), password=System.getenv("BANK_DB_PASSWORD");
   if(url==null||user==null||password==null||password.isBlank())
     throw unavailable("ACCOUNTS",new java.sql.SQLException("Missing configuration","08001"),response);
   java.util.Properties props=new java.util.Properties();
   props.setProperty("user",user);props.setProperty("password",password);
   props.setProperty("oracle.net.CONNECT_TIMEOUT","5000");props.setProperty("oracle.jdbc.ReadTimeout","10000");
   String sql="SELECT customer_number, full_name, account_number, type_name, branch_name, ifsc_code, currency_code, available_balance, account_status, opened_at FROM IBS_APP.V_IBS_CUSTOMER_ACCOUNT WHERE customer_id = ?";
   List<Map<String,Object>> rows=new ArrayList<>();
   try(var conn=java.sql.DriverManager.getConnection(url,props);var stmt=conn.prepareStatement(sql)){
     stmt.setLong(1,customerId);stmt.setQueryTimeout(5);
     try(var rs=stmt.executeQuery()){
       while(rs.next()) rows.add(Map.of("customerNumber",rs.getString(1),"customerName",rs.getString(2),"account",rs.getString(3),"accountType",rs.getString(4),"branch",rs.getString(5),"ifsc",rs.getString(6),"currency",rs.getString(7),"balance",rs.getBigDecimal(8),"status",rs.getString(9),"openedAt",rs.getTimestamp(10).toLocalDateTime().toString()));
     }
   }catch(java.sql.SQLException e){
     throw unavailable("ACCOUNTS",e,response);
   }
   return rows;
 }
 @RequestMapping({"/beneficiaries","/transfer","/transactions"}) public void disabled(){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Unavailable in read-only banking mode");}
}
