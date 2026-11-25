package com.ibs;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.nio.file.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class RuntimeHealthController {
 private Long read(String path){try{return Long.parseLong(Files.readString(Path.of(path)).trim());}catch(Exception e){return null;}}
 @GetMapping("/runtime-health") public Map<String,Object> metrics(){
 var os=ManagementFactory.getOperatingSystemMXBean();
 var mem=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
 Map<String,Object> m=new LinkedHashMap<>();
 m.put("jvmHeapUsedBytes",mem.getUsed());m.put("jvmHeapMaxBytes",mem.getMax());
 m.put("threads",ManagementFactory.getThreadMXBean().getThreadCount());
 m.put("applicationUptimeMs",ManagementFactory.getRuntimeMXBean().getUptime());
 m.put("availableProcessors",os.getAvailableProcessors());
 if(os instanceof com.sun.management.OperatingSystemMXBean ext){double cpu=ext.getProcessCpuLoad();m.put("javaProcessCpuPercent",cpu<0?null:Math.round(cpu*1000)/10.0);}
 Long used=read("/sys/fs/cgroup/memory.current"),limit=read("/sys/fs/cgroup/memory.max");
 if(used==null){used=read("/sys/fs/cgroup/memory/memory.usage_in_bytes");limit=read("/sys/fs/cgroup/memory/memory.limit_in_bytes");}
 m.put("containerMemoryUsedBytes",used);m.put("containerMemoryLimitBytes",limit!=null&&limit>0&&limit<Long.MAX_VALUE/2?limit:null);
 try{var fs=Files.getFileStore(Path.of("/tmp"));m.put("filesystemTotalBytes",fs.getTotalSpace());m.put("filesystemUsableBytes",fs.getUsableSpace());}catch(Exception e){m.put("filesystemTotalBytes",null);m.put("filesystemUsableBytes",null);}
 m.put("scope","Application runtime and visible container filesystem; not physical host metrics");
 return m;
 }
}
