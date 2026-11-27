"""Python 3.8+; run as appuser. Set RENDER_API_KEY securely."""
import os, json, sqlite3, urllib.request, urllib.parse
from pathlib import Path
from datetime import datetime, timezone, timedelta
import fcntl, gzip, re
IST = timezone(timedelta(hours=5, minutes=30))

def local_day(stamp):
    return datetime.fromisoformat(stamp.replace("Z","+00:00")).astimezone(IST).date().isoformat()

def publish(db, name, today):
    folder = ROOT/name
    folder.mkdir(parents=True, exist_ok=True)
    cutoff = (today-timedelta(days=29)).isoformat()
    buckets = {}
    for stamp,line in db.execute("SELECT stamp,line FROM logs WHERE service=? ORDER BY stamp,id",(name,)):
        day = local_day(stamp)
        if day < cutoff: continue
        kinds = ["application"]
        if name.endswith("/webserver") and re.search(r'"(?:GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS) .*HTTP/',line):
            kinds = ["access"]
        if re.search(r'\b(ERROR|FATAL|CRITICAL|Exception)\b',line,re.I):
            kinds.append("error")
        for kind in kinds:
            buckets.setdefault((day,kind),[]).append(line)
    for kind in ("application","error") + (("access",) if name.endswith("/webserver") else ()):
        buckets.setdefault((today.isoformat(),kind),[])
    for (day,kind),lines in buckets.items():
        age = (today-datetime.fromisoformat(day).date()).days
        base = folder/(kind+".log"+("" if age==0 else "."+day))
        target = Path(str(base)+".gz") if age>=2 else base
        tmp = Path(str(target)+".tmp")
        opener = gzip.open if age>=2 else open
        with opener(tmp,"wt",encoding="utf-8") as f:
            for line in lines: f.write(line+"\n")
        os.chmod(tmp,0o640)
        tmp.replace(target)
        if age>=2 and base.exists(): base.unlink()
    for p in folder.iterdir():
        m = re.fullmatch(r'(?:application|access|error)\.log\.(\d{4}-\d{2}-\d{2})(?:\.gz)?',p.name)
        if m and m[1]<cutoff: p.unlink()


ROOT = Path(os.environ.get("CLOUD_LOG_ROOT", "/home/appuser/IBS_EIMP_lab/cloud_logs"))
SERVICES = {
    "IBS/webserver": ("tea-dattr6rncjis73a0u8vg", "srv-dc34qq7avr4c73eaf4t0"),
    "IBS/appserver": ("tea-dattr6rncjis73a0u8vg", "srv-dc34o4vavr4c73ea6o80"),
    "EIMP/appserver": ("tea-dah8mkmk1f9s73e4423g", "srv-dc3qts5g1s2s73cfacag"),
    "UNIQ": ("tea-dattr6rncjis73a0u8vg", "srv-dc36drvavr4c73efl060"),
}
def iso(t):
    return t.isoformat().replace("+00:00", "Z")
def sync(db, name, owner, resource, key):
    end = iso(datetime.now(timezone.utc))
    row = db.execute("SELECT stamp FROM checkpoint WHERE service=?", (name,)).fetchone()
    start = row[0] if row else iso(datetime.now(timezone.utc)-timedelta(hours=1))
    params = dict(ownerId=owner, resource=resource, startTime=start, endTime=end, direction="forward", limit=100)
    days = set()
    seen = set()
    while True:
        cursor = (params["startTime"], params["endTime"])
        if cursor in seen:
            raise RuntimeError("Repeated pagination cursor")
        seen.add(cursor)
        req = urllib.request.Request("https://api.render.com/v1/logs?"+urllib.parse.urlencode(params), headers={"Authorization":"Bearer "+key,"Accept":"application/json"})
        with urllib.request.urlopen(req, timeout=30) as response:
            page = json.load(response)
        for event in page["logs"]:
            stamp = event["timestamp"]
            day = local_day(stamp)
            labels = {x["name"]:x["value"] for x in event.get("labels", [])}
            message = event["message"].replace("\r","\\r").replace("\n","\\n")
            line = stamp+" "+labels.get("level","unknown").upper()+" "+message
            db.execute("INSERT OR IGNORE INTO logs VALUES (?,?,?,?,?)",(name,event["id"],stamp,day,line))
            days.add(day)
        if not page.get("hasMore"):
            break
        params.update(startTime=page["nextStartTime"],endTime=page["nextEndTime"])
    publish(db,name,datetime.now(IST).date())
    db.execute("INSERT OR REPLACE INTO checkpoint VALUES (?,?)",(name,end))
    db.commit()

def main():
    os.umask(0o077)
    key = os.environ["RENDER_API_KEY"]
    ROOT.mkdir(parents=True,exist_ok=True)
    with (ROOT/".sync.lock").open("a") as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        with sqlite3.connect(ROOT/".sync.sqlite") as db:
            db.execute("CREATE TABLE IF NOT EXISTS logs(service TEXT,id TEXT,stamp TEXT,day TEXT,line TEXT,PRIMARY KEY(service,id))")
            db.execute("CREATE TABLE IF NOT EXISTS checkpoint(service TEXT PRIMARY KEY,stamp TEXT)")
            failed = False
            for name,(owner,resource) in SERVICES.items():
                try:
                    sync(db,name,owner,resource,key)
                    print(iso(datetime.now(timezone.utc)),name,"OK")
                except Exception as exc:
                    db.rollback()
                    failed = True
                    print(iso(datetime.now(timezone.utc)),name,"FAILED",type(exc).__name__)
            if failed:
                raise SystemExit(1)
if __name__ == "__main__":
    main()
