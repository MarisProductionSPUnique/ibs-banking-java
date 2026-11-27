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

if __name__ == "__main__":
    unittest.main()
