import json, os, time, urllib.request, urllib.error, http.cookiejar
from datetime import datetime, timezone, timedelta

base = "http://127.0.0.1:8080"
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def call(path, expected=200, body=None, headers=None):
    h = {"X-IBS-Client":"IBS-DEMO", **(headers or {})}
    if body is not None:
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(base+path, data=None if body is None else json.dumps(body).encode(), headers=h)
    try:
        r = client.open(req, timeout=10)
    except urllib.error.HTTPError as e:
        r = e
    with r:
        raw = r.read()
        assert r.status == expected, (path, r.status, expected)
        print(path, r.status, flush=True)
        return json.loads(raw) if "application/json" in r.headers.get("Content-Type","") else raw.decode()

for i in range(60):
    try:
        assert call("/api/health")["status"] == "UP"
        break
    except (OSError, AssertionError):
        time.sleep(1)
else:
    raise RuntimeError("Application startup failed")
call("/api/accounts",401)
call("/api/login",401,{"username":"demo","password":"wrong"})
call("/api/login",body={"username":"demo","password":"IBSdemo123"})
assert call("/api/accounts")[0]["balance"] == 25000
call("/api/beneficiaries",body={"account":"DEMO002"})
payload={"account":"DEMO002","amount":"100.00"}
headers={"Idempotency-Key":"ci-test-1"}
first=call("/api/transfer",body=payload,headers=headers)
assert first["status"] == "SUCCESS"
assert call("/api/transfer",body=payload,headers=headers)==first
call("/api/transfer",409,{"account":"DEMO002","amount":"200.00"},headers)
assert call("/api/accounts")[0]["balance"] == 24900
assert len(call("/api/transactions")) == 1
call("/api/support/logs",401)
auth={"X-Support-Key":os.environ["IBS_SUPPORT_KEY"]}
assert call("/api/support/logs?status=409",headers=auth)
call("/api/support/logs?from=invalid",400,headers=auth)
call("/api/support/logs?from=2026-01-02T00:00:00Z&to=2026-01-01T00:00:00Z",400,headers=auth)
assert call("/api/support/logs?from=2099-01-01T00:00:00Z",headers=auth)==[]
assert "status=409" in call("/api/support/logs/download?status=409",headers=auth)
assert call("/api/runtime-health")["threads"] > 0
for page in ("/","/logs.html","/monitor.html"):
    assert call(page)
call("/api/logout",body={})
call("/api/accounts",401)
print("All IBS smoke tests passed")
