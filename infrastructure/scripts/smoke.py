"""Bounded smoke and persistence checks. Run only against an isolated test stack.
Creates then removes a temporary paper portfolio; no provider requests are made.
"""
import json
import urllib.request
import uuid
import subprocess
import sys
import time

BASE = "http://127.0.0.1:5173/api/v1"


def call(path, method="GET", body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(BASE + path, data=data, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=15) as response:
        payload = response.read()
        return json.loads(payload) if payload else None


for url in ("http://127.0.0.1:5173/", "http://127.0.0.1:8000/health", "http://127.0.0.1:8080/actuator/health", "http://127.0.0.1:8080/v3/api-docs"):
    with urllib.request.urlopen(url, timeout=15) as response:
        assert response.status == 200, url
    print("PASS", url)
status = call("/system/status")
assert all(status[key]["status"] == "UP" for key in ("spring", "postgres", "ml"))
asset = call("/assets/search?q=AAPL")["items"][0]
portfolio = call("/portfolios", "POST", {"name": "Smoke " + uuid.uuid4().hex[:8], "base_currency": "USD"})
pid = portfolio["id"]
try:
    call(f"/portfolios/{pid}/holdings", "POST", {"asset_id": asset["id"], "quantity": "2", "average_purchase_price": "100.10"})
    saved = call(f"/portfolios/{pid}")
    assert saved["holdings"][0]["quantity"] == 2
    assert saved["invested_capital"] == 200.20
    if "--restart-database" in sys.argv:
        subprocess.run(["docker", "compose", "restart", "postgres"], check=True, timeout=45)
        for attempt in range(10):
            try:
                with urllib.request.urlopen("http://127.0.0.1:8080/actuator/health", timeout=3) as readiness:
                    if readiness.status == 200:
                        break
            except Exception:
                if attempt == 9:
                    raise
                time.sleep(1)
        persisted = call(f"/portfolios/{pid}")
        assert persisted["holdings"][0]["quantity"] == 2
        print("PASS database volume persistence after normal PostgreSQL restart")
    hid = saved["holdings"][0]["id"]
    call(f"/portfolios/{pid}/holdings/{hid}", "PUT", {"asset_id": asset["id"], "quantity": "3", "average_purchase_price": "100.10"})
    assert call(f"/portfolios/{pid}")["holdings"][0]["quantity"] == 3
    call(f"/portfolios/{pid}/holdings/{hid}", "DELETE")
    assert call(f"/portfolios/{pid}")["holdings"] == []
    print("PASS portfolio persistence across HTTP requests and frontend proxy")
finally:
    call(f"/portfolios/{pid}", "DELETE")
print("PASS Java-to-Python connectivity and database readiness")
