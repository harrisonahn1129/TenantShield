"""
test_data_agent.py
==================
Run this before the hackathon to confirm all four NYC APIs respond correctly.
Uses the demo building address — update DEMO_ADDRESS if you change buildings.

Usage:
    python test_data_agent.py

All tests should print OK. If any fail, check your .env keys and
verify the address matches a real building with HPD violations.
"""

import requests
import json

# ─────────────────────────────────────────────
# Your demo building — update this to the
# building Teammate C found on HPD Online
# ─────────────────────────────────────────────
DEMO_ADDRESS  = "456 Nostrand Ave"
DEMO_BOROUGH  = "Brooklyn"
DEMO_HOUSE    = "456"
DEMO_STREET   = "Nostrand Ave"
DEMO_BOROUGH_ID = "3"
DEMO_BBL      = "3012300045"   # update after running Geoclient test

SODA = "https://data.cityofnewyork.us/resource"

# ── Test 1: HPD Violations ────────────────────────────────────────────

def test_hpd_violations():
    print("\n[1] HPD Violations (wvxf-dwi5)...")
    r = requests.get(f"{SODA}/wvxf-dwi5.json", params={
        "bbl": DEMO_BBL,
        "currentstatus": "OPEN",
        "$order": "inspectiondate DESC",
        "$limit": 5,
    })
    data = r.json()
    assert isinstance(data, list), f"Expected list, got: {type(data)}"
    print(f"  OK — {len(data)} open violations found")
    if data:
        v = data[0]
        print(f"  Latest: Class {v.get('class')} — {v.get('novdescription', '')[:60]}")

# ── Test 2: HPD Complaints ────────────────────────────────────────────

def test_hpd_complaints():
    print("\n[2] HPD Complaints (ygpa-z7cr)...")
    r = requests.get(f"{SODA}/ygpa-z7cr.json", params={
        "housenumber": DEMO_HOUSE,
        "streetname":  DEMO_STREET.upper(),
        "boroughid":   DEMO_BOROUGH_ID,
        "openstatus":  "Open",
        "$order":      "opendate DESC",
        "$limit":      5,
    })
    data = r.json()
    assert isinstance(data, list), f"Expected list, got: {type(data)}"
    print(f"  OK — {len(data)} open complaints found")
    if data:
        c = data[0]
        print(f"  Oldest open: {c.get('opendate', '')[:10]} — {c.get('majorcategoryid', '')}")

# ── Test 3: DOB Complaints ────────────────────────────────────────────

def test_dob_complaints():
    print("\n[3] DOB Complaints (eabe-havv)...")
    r = requests.get(f"{SODA}/eabe-havv.json", params={
        "house_number": DEMO_HOUSE,
        "house_street": DEMO_STREET.upper(),
        "status":       "ACTIVE",
        "$limit":       5,
    })
    data = r.json()
    assert isinstance(data, list), f"Expected list, got: {type(data)}"
    print(f"  OK — {len(data)} active DOB complaints found")
    if data:
        d = data[0]
        print(f"  Latest: {d.get('complaint_category', '')} — {d.get('date_entered', '')[:10]}")

# ── Test 4: HPD Registration (owner info) ────────────────────────────

def test_owner_info():
    print("\n[4] HPD Registration (tesw-yqqr)...")
    r = requests.get(f"{SODA}/tesw-yqqr.json", params={
        "housenumber": DEMO_HOUSE,
        "streetname":  DEMO_STREET.upper(),
        "boroid":      DEMO_BOROUGH_ID,
        "$limit":      1,
    })
    data = r.json()
    assert isinstance(data, list), f"Expected list, got: {type(data)}"
    if data:
        rec = data[0]
        owner = f"{rec.get('ownerfirstname','')} {rec.get('ownerlastname','')}".strip()
        corp  = rec.get("corporationname", "")
        reg   = rec.get("registrationid", "")
        print(f"  OK — Owner: {owner or '(none)'}")
        print(f"  Corp: {corp or '(none)'}")
        print(f"  Reg ID: {reg}")
        print(f"  HPD Online: https://hpdonline.nyc.gov/hpdonline/building/{reg}")
    else:
        print("  WARNING — no registration found for this address")
        print("  Try a different building or check the address spelling")

# ── Test 5: Full agent endpoint ───────────────────────────────────────

def test_full_endpoint():
    print("\n[5] Full /lookup endpoint...")
    try:
        r = requests.post("http://localhost:8001/lookup", json={
            "address": DEMO_ADDRESS,
            "borough": DEMO_BOROUGH,
        }, timeout=30)
        data = r.json()
        print(f"  OK — {data.get('violation_count', 0)} violations")
        print(f"  Owner: {data.get('owner_name', '(none)')}")
        print(f"  LLC: {data.get('corporation_name', '(none)')}")
        print(f"  Days neglected: {data.get('days_neglected', 0)}")
        print(f"  DOB complaints: {data.get('dob_count', 0)}")
        if data.get("knowledge_graph"):
            kg = data["knowledge_graph"]
            print(f"  Knowledge graph: {kg.get('total_buildings', 0)} buildings, "
                  f"{kg.get('total_violations', 0)} total violations")
    except requests.exceptions.ConnectionError:
        print("  SKIP — server not running (start with: uvicorn data_agent:app --port 8001)")

# ─────────────────────────────────────────────
# Run all tests
# ─────────────────────────────────────────────

if __name__ == "__main__":
    print("=" * 55)
    print("TenantShield Data Agent — API Tests")
    print(f"Building: {DEMO_ADDRESS}, {DEMO_BOROUGH}")
    print("=" * 55)

    test_hpd_violations()
    test_hpd_complaints()
    test_dob_complaints()
    test_owner_info()
    test_full_endpoint()

    print("\n" + "=" * 55)
    print("Done. Fix any WARNING lines before the hackathon.")
    print("=" * 55)
