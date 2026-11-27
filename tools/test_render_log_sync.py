import unittest, tempfile, sqlite3, gzip
from pathlib import Path
from datetime import date
import render_log_sync as collector

class RotationTest(unittest.TestCase):
    def test_rotation(self):
        with tempfile.TemporaryDirectory() as tmp:
            original = collector.ROOT
            collector.ROOT = Path(tmp)
            try:
                with sqlite3.connect(":memory:") as db:
                    db.execute("CREATE TABLE logs(service TEXT,id TEXT,stamp TEXT,day TEXT,line TEXT,PRIMARY KEY(service,id))")
                    name = "IBS/webserver"
                    rows = [
                        ("1","2026-11-26T18:29:59Z","ERROR old"),
                        ("2","2026-11-26T18:30:00Z",' "GET / HTTP/1.1" 200'),
                        ("3","2026-11-25T10:00:00Z","INFO compressed"),
                        ("4","2026-10-01T10:00:00Z","INFO expired"),
                    ]
                    for ident,stamp,line in rows+rows:
                        db.execute("INSERT OR IGNORE INTO logs VALUES (?,?,?,?,?)",(name,ident,stamp,collector.local_day(stamp),line))
                    folder = Path(tmp)/name
                    folder.mkdir(parents=True)
                    expired = folder/"application.log.2026-10-01.gz"
                    expired.touch()
                    collector.publish(db,name,date(2026,11,27))
                    self.assertEqual((folder/"access.log").read_text().count("GET"),1)
                    self.assertIn("ERROR old",(folder/"error.log.2026-11-26").read_text())
                    self.assertEqual((folder/"error.log").read_text(),"")
                    with gzip.open(folder/"application.log.2026-11-25.gz","rt") as f:
                        self.assertIn("INFO compressed",f.read())
                    self.assertFalse(expired.exists())
                    self.assertEqual((folder/"access.log").stat().st_mode & 0o777,0o640)
                    collector.publish(db,name,date(2026,11,28))
                    self.assertIn("GET",(folder/"access.log.2026-11-27").read_text())
                    self.assertEqual((folder/"access.log").read_text(),"")
            finally:
                collector.ROOT = original

class SyncTest(unittest.TestCase):
    def test_pagination_and_retention(self):
        from unittest.mock import patch
        from datetime import datetime, timedelta, timezone
        import json, io, urllib.parse
        now = datetime.now(timezone.utc)
        stamp = collector.iso(now-timedelta(minutes=5))
        checkpoint = collector.iso(now-timedelta(minutes=2))
        event = dict(id="delayed",timestamp=stamp,message="INFO delayed",labels=[])
        pages = [
            dict(logs=[event],hasMore=True,nextStartTime=stamp,nextEndTime=collector.iso(now)),
            dict(logs=[event],hasMore=False)
        ]
        with tempfile.TemporaryDirectory() as tmp, sqlite3.connect(":memory:") as db:
            db.execute("CREATE TABLE logs(service TEXT,id TEXT,stamp TEXT,day TEXT,line TEXT,PRIMARY KEY(service,id))")
            db.execute("CREATE TABLE checkpoint(service TEXT PRIMARY KEY,stamp TEXT)")
            name = "IBS/appserver"
            db.execute("INSERT INTO checkpoint VALUES (?,?)",(name,checkpoint))
            db.execute("INSERT INTO logs VALUES (?,?,?,?,?)",(name,"expired","2000-01-01T00:00:00Z","2000-01-01","expired"))
            with patch.object(collector,"ROOT",Path(tmp)), patch.object(collector.urllib.request,"urlopen",side_effect=[io.BytesIO(json.dumps(p).encode()) for p in pages]) as api:
                collector.sync(db,name,"owner","resource","test-key")
                self.assertEqual(api.call_count,2)
                query = urllib.parse.parse_qs(urllib.parse.urlparse(api.call_args_list[0].args[0].full_url).query)
                self.assertEqual(query["startTime"][0],collector.iso(now-timedelta(minutes=12)))
                self.assertEqual(db.execute("SELECT id FROM logs").fetchall(),[("delayed",)])
                self.assertIn("INFO delayed",(Path(tmp)/name/"application.log").read_text())

class ReviewTest(unittest.TestCase):
    def test_http_errors_and_unchanged_archive(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp, sqlite3.connect(":memory:") as db:
            db.execute("CREATE TABLE logs(service TEXT,id TEXT,stamp TEXT,day TEXT,line TEXT,PRIMARY KEY(service,id))")
            name = "IBS/webserver"
            for status in (200,404,503):
                db.execute("INSERT INTO logs VALUES (?,?,?,?,?)",(name,str(status),"2026-11-25T10:00:00Z","2026-11-25",f'"GET / HTTP/1.1" {status}'))
            with patch.object(collector,"ROOT",Path(tmp)):
                collector.publish(db,name,date(2026,11,27))
                target = Path(tmp)/name/"error.log.2026-11-25.gz"
                with gzip.open(target,"rt") as f:
                    data = f.read()
                self.assertIn("404",data)
                self.assertIn("503",data)
                self.assertNotIn("200",data)
                inode = target.stat().st_ino
                collector.publish(db,name,date(2026,11,27))
                self.assertEqual(target.stat().st_ino,inode)

    def test_retry(self):
        from unittest.mock import patch
        import io, urllib.error
        with tempfile.TemporaryDirectory() as tmp, sqlite3.connect(":memory:") as db:
            db.execute("CREATE TABLE logs(service TEXT,id TEXT,stamp TEXT,day TEXT,line TEXT,PRIMARY KEY(service,id))")
            db.execute("CREATE TABLE checkpoint(service TEXT PRIMARY KEY,stamp TEXT)")
            with patch.object(collector,"ROOT",Path(tmp)), patch.object(collector.time,"sleep"), patch.object(collector.urllib.request,"urlopen",side_effect=[urllib.error.URLError("temporary"),io.BytesIO(b'{"logs":[],"hasMore":false}')]) as api:
                collector.sync(db,"UNIQ","owner","resource","test-key")
                self.assertEqual(api.call_count,2)
                self.assertIsNotNone(db.execute("SELECT stamp FROM checkpoint WHERE service='UNIQ'").fetchone())

if __name__ == "__main__":
    unittest.main()
