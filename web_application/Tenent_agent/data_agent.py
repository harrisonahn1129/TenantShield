"""
TenantShield — Data Agent
=========================
A2A-compatible agent that handles all NYC Open Data lookups.
Deployed separately to Cloud Run. Called by the Orchestrator Agent.

Datasets used:
  - wvxf-dwi5  HPD Housing Maintenance Code Violations
  - ygpa-z7cr  HPD Housing Maintenance Code Complaints and Problems
  - eabe-havv  DOB Complaints Received
  - tesw-yqqr  HPD Multiple Dwelling Registrations (owner info)
  - bnx9-e6tj  ACRIS Real Property Master (deeds, mortgages, sale amounts)
  - 8h5j-fqxa  ACRIS Real Property Legals (deed / LLC chain)
  - 636b-3b5g  ACRIS Real Property Parties (owner names on deed)

External APIs:
  - NYC Geoclient API  (address → BBL + BIN)

Run locally:
  pip install fastapi uvicorn requests python-dotenv
  uvicorn data_agent:app --reload --port 8001

Deploy to Cloud Run:
  gemini deploy --name tenantshield-data --source .
"""

import os
import requests
from datetime import datetime
from typing import Optional
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from dotenv import load_dotenv

load_dotenv()

# ─────────────────────────────────────────────
# Config
# ─────────────────────────────────────────────

SODA_BASE = "https://data.cityofnewyork.us/resource"
GEOCLIENT_BASE = "https://api.cityofnewyork.us/geoclient/v2"

GEOCLIENT_APP_ID  = os.getenv("GEOCLIENT_APP_ID")
GEOCLIENT_APP_KEY = os.getenv("GEOCLIENT_APP_KEY")

# Borough name → boroughid used by HPD complaints dataset
BOROUGH_IDS = {
    "manhattan": "1",
    "bronx":     "2",
    "brooklyn":  "3",
    "queens":    "4",
    "staten island": "5",
}

# ─────────────────────────────────────────────
# FastAPI app
# ─────────────────────────────────────────────

app = FastAPI(
    title="TenantShield Data Agent",
    description="NYC Open Data lookups for TenantShield Live",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─────────────────────────────────────────────
# Request / Response models
# ─────────────────────────────────────────────

class BuildingRequest(BaseModel):
    address: str          # e.g. "456 Nostrand Ave"
    borough: str          # e.g. "Brooklyn"
    apartment: Optional[str] = None

class BuildingResponse(BaseModel):
    address: str
    borough: str
    bbl: Optional[str]
    bin: Optional[str]
    registration_id: Optional[str]
    owner_name: Optional[str]
    owner_phone: Optional[str]
    corporation_name: Optional[str]
    manager_name: Optional[str]
    manager_phone: Optional[str]
    hpd_online_url: Optional[str]
    violation_count: int
    open_violations: int
    class_a_count: int
    class_b_count: int
    class_c_count: int
    highest_class: str
    violations: list
    complaints: list
    complaints_count: int
    dob_complaints: list
    dob_count: int
    days_neglected: int
    oldest_complaint_date: Optional[str]
    knowledge_graph: Optional[dict]
    error: Optional[str] = None

# ─────────────────────────────────────────────
# Helper: SODA query
# ─────────────────────────────────────────────

def soda_get(dataset_id: str, params: dict) -> list:
    """
    Query any NYC Open Data SODA endpoint.
    Returns list of records, or empty list on failure.
    """
    url = f"{SODA_BASE}/{dataset_id}.json"
    try:
        resp = requests.get(url, params=params, timeout=10)
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        print(f"[SODA] {dataset_id} error: {e}")
        return []

# ─────────────────────────────────────────────
# Step 1 — Address → BBL via Geoclient
# ─────────────────────────────────────────────

def address_to_bbl(house_number: str, street: str, borough: str) -> dict:
    """
    Convert a street address to BBL and BIN using NYC Geoclient API.
    Returns dict with bbl, bin, latitude, longitude.
    Returns empty dict if lookup fails.

    BBL = Borough-Block-Lot — the primary key for all NYC property data.
    BIN = Building Identification Number — used by DOB complaints.
    """
    if not GEOCLIENT_APP_ID or not GEOCLIENT_APP_KEY:
        print("[Geoclient] No credentials — skipping BBL lookup")
        return {}

    try:
        resp = requests.get(
            f"{GEOCLIENT_BASE}/address.json",
            params={
                "houseNumber": house_number,
                "street":      street,
                "borough":     borough,
                "app_id":      GEOCLIENT_APP_ID,
                "app_key":     GEOCLIENT_APP_KEY,
            },
            timeout=10,
        )
        resp.raise_for_status()
        data = resp.json().get("address", {})
        return {
            "bbl": data.get("bbl"),
            "bin": data.get("buildingIdentificationNumber"),
            "latitude":  data.get("latitudeInternalLabel"),
            "longitude": data.get("longitudeInternalLabel"),
        }
    except Exception as e:
        print(f"[Geoclient] Error: {e}")
        return {}

# ─────────────────────────────────────────────
# Step 2 — Owner info via HPD Registration
# ─────────────────────────────────────────────

def get_owner_info(house_number: str, street: str, borough: str) -> dict:
    """
    Query HPD Multiple Dwelling Registration dataset (tesw-yqqr).
    Returns owner name, phone, manager name, corporation name.
    This is the fastest path to unmasking the LLC owner.

    Key fields returned:
      ownerfirstname, ownerlastname  → the human being
      corporationname                → the LLC name
      managerfirstname, managerlastname, managerphone
      registrationid                 → used to build HPD Online deep link
    """
    records = soda_get("tesw-yqqr", {
        "housenumber": house_number,
        "streetname":  street.upper(),
        "boroid":      BOROUGH_IDS.get(borough.lower(), "3"),
        "$limit": 1,
        "$order": "lastregistrationdate DESC",
    })

    if not records:
        return {}

    r = records[0]

    owner_first = r.get("ownerfirstname", "")
    owner_last  = r.get("ownerlastname", "")
    owner_name  = f"{owner_first} {owner_last}".strip() or None

    mgr_first = r.get("managerfirstname", "")
    mgr_last  = r.get("managerlastname", "")
    mgr_name  = f"{mgr_first} {mgr_last}".strip() or None

    reg_id = r.get("registrationid")

    return {
        "registration_id":   reg_id,
        "owner_name":        owner_name,
        "owner_phone":       r.get("ownerphone") or None,
        "corporation_name":  r.get("corporationname") or None,
        "manager_name":      mgr_name,
        "manager_phone":     r.get("managerphone") or None,
        "hpd_online_url":    f"https://hpdonline.nyc.gov/hpdonline/building/{reg_id}" if reg_id else None,
    }

# ─────────────────────────────────────────────
# Step 3 — HPD Violations (wvxf-dwi5)
# ─────────────────────────────────────────────

def get_hpd_violations(bbl: str) -> list:
    """
    Query HPD Housing Maintenance Code Violations by BBL.
    Returns all OPEN violations, sorted newest first.

    Each violation has:
      class         → A / B / C (C = immediately hazardous)
      novdescription → plain-English hazard description
      ordernumber   → official HMC reference for complaint PDF
      inspectiondate
      apartment
    """
    records = soda_get("wvxf-dwi5", {
        "bbl":           bbl,
        "currentstatus": "OPEN",
        "$order":        "inspectiondate DESC",
        "$limit":        100,
    })

    cleaned = []
    for r in records:
        cleaned.append({
            "violation_id":  r.get("violationid"),
            "class":         r.get("class", "").upper(),
            "description":   r.get("novdescription", ""),
            "order_number":  r.get("ordernumber"),
            "status":        r.get("currentstatus", ""),
            "date":          r.get("inspectiondate", "")[:10] if r.get("inspectiondate") else None,
            "apartment":     r.get("apartment"),
            "story":         r.get("story"),
        })
    return cleaned

# ─────────────────────────────────────────────
# Step 4 — HPD Complaints (ygpa-z7cr)
# ─────────────────────────────────────────────

def get_hpd_complaints(house_number: str, street: str, borough: str) -> dict:
    """
    Query HPD Housing Maintenance Code Complaints and Problems.
    This is the tenant-reported side — what people called 311 about,
    BEFORE an inspector ever visited.

    The days_neglected value is the most powerful number in TenantShield:
    "A tenant reported this problem 247 days ago. It is still open."

    Returns both the records and a calculated neglect score.
    """
    borough_id = BOROUGH_IDS.get(borough.lower(), "3")

    records = soda_get("ygpa-z7cr", {
        "housenumber": house_number,
        "streetname":  street.upper(),
        "boroughid":   borough_id,
        "openstatus":  "Open",
        "$order":      "opendate DESC",
        "$limit":      50,
    })

    if not records:
        return {"records": [], "days_neglected": 0, "oldest_date": None}

    # Calculate how long the oldest open complaint has been sitting
    oldest_date = None
    days_neglected = 0

    open_dates = [r.get("opendate", "") for r in records if r.get("opendate")]
    if open_dates:
        oldest_str = min(open_dates)
        try:
            oldest_dt   = datetime.fromisoformat(oldest_str[:10])
            now         = datetime.now()
            days_neglected = (now - oldest_dt).days
            oldest_date = oldest_str[:10]
        except Exception:
            pass

    cleaned = []
    for r in records:
        cleaned.append({
            "complaint_id":  r.get("complaintid"),
            "type":          r.get("type", ""),
            "category":      r.get("majorcategoryid", ""),
            "status":        r.get("statuscdescription", ""),
            "open_date":     r.get("opendate", "")[:10] if r.get("opendate") else None,
            "apartment":     r.get("apartment"),
        })

    return {
        "records":         cleaned,
        "days_neglected":  days_neglected,
        "oldest_date":     oldest_date,
    }

# ─────────────────────────────────────────────
# Step 5 — DOB Complaints (eabe-havv)
# ─────────────────────────────────────────────

def get_dob_complaints(house_number: str, street: str) -> list:
    """
    Query DOB Complaints Received dataset.
    Covers structural complaints, illegal construction,
    elevator problems — things HPD does NOT handle.

    Having DOB complaints on top of HPD violations means TWO
    city agencies have flagged this building. Show that in the UI.
    """
    records = soda_get("eabe-havv", {
        "house_number": house_number,
        "house_street": street.upper(),
        "status":       "ACTIVE",
        "$order":       "date_entered DESC",
        "$limit":       20,
    })

    cleaned = []
    for r in records:
        cleaned.append({
            "complaint_number": r.get("complaint_number"),
            "category":         r.get("complaint_category", ""),
            "status":           r.get("status", ""),
            "date_entered":     r.get("date_entered", "")[:10] if r.get("date_entered") else None,
            "disposition_date": r.get("disposition_date", "")[:10] if r.get("disposition_date") else None,
            "bin":              r.get("bin"),
        })
    return cleaned

# ─────────────────────────────────────────────
# Step 6 — Knowledge Graph (owner network)
# ─────────────────────────────────────────────

def get_property_transactions(document_ids: list) -> list:
    """
    Query ACRIS Real Property Master (bnx9-e6tj) for document details.
    Returns deed/mortgage info: type, date, amount.
    This shows when the property was last sold and for how much.
    """
    if not document_ids:
        return []

    transactions = []
    for doc_id in document_ids[:10]:
        records = soda_get("bnx9-e6tj", {
            "document_id": doc_id,
            "$limit": 1,
        })
        if records:
            r = records[0]
            transactions.append({
                "document_id": doc_id,
                "doc_type": r.get("doc_type", ""),
                "document_date": r.get("document_date", "")[:10] if r.get("document_date") else None,
                "document_amt": r.get("document_amt"),
                "recorded_datetime": r.get("recorded_datetime", "")[:10] if r.get("recorded_datetime") else None,
            })

    return transactions


def get_acris_legals_for_bbl(borough: str, block: str, lot: str) -> list:
    """
    Query ACRIS Real Property Legals (8h5j-fqxa) to find
    all document_ids associated with a BBL.
    Returns document_ids for deeds and mortgages.
    """
    records = soda_get("8h5j-fqxa", {
        "borough": borough,
        "block": block,
        "lot": lot,
        "$order": "good_through_date DESC",
        "$limit": 20,
    })
    return records


def get_acris_parties(document_id: str) -> list:
    """
    Query ACRIS Real Property Parties (636b-3b5g) for a document.
    party_type 1 = grantor (seller), 2 = grantee (buyer/owner).
    """
    return soda_get("636b-3b5g", {
        "document_id": document_id,
        "$limit": 10,
    })


def get_property_history(bbl: str) -> dict:
    """
    Build full property transaction history using ACRIS:
      Legals (BBL → document_ids) → Master (document details) → Parties (names)

    Returns last sale info, current owner from deed, and mortgage info.
    """
    if not bbl or len(bbl) < 10:
        return {}

    borough = bbl[0]
    block = str(int(bbl[1:6]))   # strip leading zeros — ACRIS uses unpadded
    lot = str(int(bbl[6:10]))

    # Get all documents for this property
    legals = get_acris_legals_for_bbl(borough, block, lot)
    if not legals:
        return {}

    doc_ids = list({r.get("document_id") for r in legals if r.get("document_id")})

    # Get transaction details from Property Master
    transactions = get_property_transactions(doc_ids)

    # Find the latest DEED and latest MTGE
    deeds = [t for t in transactions if t.get("doc_type") == "DEED"]
    mortgages = [t for t in transactions if t.get("doc_type") == "MTGE"]

    result = {
        "total_transactions": len(transactions),
        "deeds_count": len(deeds),
        "mortgages_count": len(mortgages),
    }

    # Latest deed — last sale
    if deeds:
        latest_deed = deeds[0]  # already ordered DESC
        result["last_sale_date"] = latest_deed.get("document_date")
        result["last_sale_amount"] = latest_deed.get("document_amt")
        result["last_sale_recorded"] = latest_deed.get("recorded_datetime")

        # Get buyer/seller from parties
        parties = get_acris_parties(latest_deed["document_id"])
        grantors = [p.get("name", "") for p in parties if p.get("party_type") == "1"]
        grantees = [p.get("name", "") for p in parties if p.get("party_type") == "2"]
        result["deed_sellers"] = grantors
        result["deed_buyers"] = grantees

    # Latest mortgage
    if mortgages:
        latest_mtge = mortgages[0]
        result["latest_mortgage_date"] = latest_mtge.get("document_date")
        result["latest_mortgage_amount"] = latest_mtge.get("document_amt")

    return result


def build_owner_graph(owner_name: str) -> dict:
    """
    Find all properties owned by the same person/entity via ACRIS.
    Builds a simple graph: owner → LLCs → buildings → violations.

    This is the "George Benton owns 7 buildings, 68 total violations"
    moment that wins the hackathon demo.

    Uses:
      ACRIS Real Property Parties (636b-3b5g) — find all deeds with this name
      ACRIS Real Property Legals  (8h5j-fqxa) — get BBLs for each deed
      HPD Violations (wvxf-dwi5)              — get violation count per BBL
    """
    if not owner_name:
        return {}

    # Search ACRIS parties for all deeds where this name is the grantee (buyer)
    # party_type "2" = grantee (current owner)
    parties = soda_get("636b-3b5g", {
        "name":       owner_name.upper(),
        "party_type": "2",
        "$select":    "document_id,name,address_1",
        "$limit":     50,
    })

    if not parties:
        return {
            "owner": owner_name,
            "total_buildings": 0,
            "buildings": [],
            "total_violations": 0,
        }

    doc_ids = list({p["document_id"] for p in parties})[:20]

    # Get BBLs for each document
    buildings_seen = set()
    buildings = []
    total_violations = 0

    for doc_id in doc_ids:
        legals = soda_get("8h5j-fqxa", {
            "document_id": doc_id,
            "$select":     "bbl,property_type",
            "$limit":      5,
        })
        for legal in legals:
            bbl = legal.get("bbl")
            if not bbl or bbl in buildings_seen:
                continue
            buildings_seen.add(bbl)

            # Get violation count for this building
            violations = soda_get("wvxf-dwi5", {
                "bbl":           bbl,
                "currentstatus": "OPEN",
                "$select":       "class,count(*) as cnt",
                "$group":        "class",
            })

            v_count  = sum(int(v.get("cnt", 0)) for v in violations)
            c_count  = next((int(v["cnt"]) for v in violations if v.get("class") == "C"), 0)
            total_violations += v_count

            buildings.append({
                "bbl":             bbl,
                "violation_count": v_count,
                "class_c_count":   c_count,
            })

    # Sort worst buildings first
    buildings.sort(key=lambda b: b["violation_count"], reverse=True)
    worst = buildings[0] if buildings else None

    return {
        "owner":           owner_name,
        "total_buildings": len(buildings),
        "buildings":       buildings,
        "total_violations": total_violations,
        "worst_building":  worst,
    }

# ─────────────────────────────────────────────
# Parse address helper
# ─────────────────────────────────────────────

def parse_address(address: str) -> tuple[str, str]:
    """
    Split "456 Nostrand Ave" into house_number="456", street="Nostrand Ave".
    Handles simple addresses — good enough for a hackathon.
    """
    parts = address.strip().split(" ", 1)
    if len(parts) == 2 and parts[0].rstrip(",.").isdigit():
        return parts[0], parts[1]
    return "", address

# ─────────────────────────────────────────────
# Main endpoint
# ─────────────────────────────────────────────

@app.post("/lookup", response_model=BuildingResponse)
async def lookup_building(req: BuildingRequest):
    """
    Full building lookup — one address in, full story out.

    Called by the Orchestrator Agent when the tenant says their address.
    Returns everything needed for the owner reveal screen, knowledge graph,
    and complaint PDF.

    Expected response time: 3–6 seconds (parallel queries would be faster
    but sequential is simpler for the hackathon).
    """
    house_number, street = parse_address(req.address)

    if not house_number:
        raise HTTPException(400, "Could not parse house number from address")

    # ── 1. Get BBL from Geoclient ──────────────────────────────────────
    geo = address_to_bbl(house_number, street, req.borough)
    bbl = geo.get("bbl")
    bin_num = geo.get("bin")

    if not bbl:
        # Fall back to address-based queries — still works for most datasets
        print(f"[lookup] No BBL for {req.address} — using address fallback")

    # ── 2. Owner info ──────────────────────────────────────────────────
    owner = get_owner_info(house_number, street, req.borough)

    # ── 3. HPD Violations ─────────────────────────────────────────────
    violations = get_hpd_violations(bbl) if bbl else []

    class_counts = {"A": 0, "B": 0, "C": 0}
    for v in violations:
        cls = v.get("class", "").upper()
        if cls in class_counts:
            class_counts[cls] += 1

    highest_class = "A"
    if class_counts["C"] > 0:
        highest_class = "C"
    elif class_counts["B"] > 0:
        highest_class = "B"

    # ── 4. HPD Complaints ─────────────────────────────────────────────
    complaint_data = get_hpd_complaints(house_number, street, req.borough)

    # ── 5. DOB Complaints ─────────────────────────────────────────────
    dob_complaints = get_dob_complaints(house_number, street)

    # ── 6. Knowledge Graph ────────────────────────────────────────────
    #    Only run if we have an owner name — skip if lookup failed
    graph = None
    owner_name = owner.get("owner_name") or owner.get("corporation_name")
    if owner_name:
        graph = build_owner_graph(owner_name)

    # ── 7. Property transaction history (ACRIS Master) ────────────────
    property_history = get_property_history(bbl) if bbl else {}
    if graph and property_history:
        graph["property_history"] = property_history

    # ── 8. Assemble and return ────────────────────────────────────────
    return BuildingResponse(
        address=f"{house_number} {street}, {req.borough}",
        borough=req.borough,
        bbl=bbl,
        bin=bin_num,
        registration_id=owner.get("registration_id"),
        owner_name=owner.get("owner_name"),
        owner_phone=owner.get("owner_phone"),
        corporation_name=owner.get("corporation_name"),
        manager_name=owner.get("manager_name"),
        manager_phone=owner.get("manager_phone"),
        hpd_online_url=owner.get("hpd_online_url"),
        violation_count=len(violations),
        open_violations=len(violations),
        class_a_count=class_counts["A"],
        class_b_count=class_counts["B"],
        class_c_count=class_counts["C"],
        highest_class=highest_class,
        violations=violations[:20],         # top 20 for UI display
        complaints=complaint_data["records"],
        complaints_count=len(complaint_data["records"]),
        dob_complaints=dob_complaints,
        dob_count=len(dob_complaints),
        days_neglected=complaint_data["days_neglected"],
        oldest_complaint_date=complaint_data["oldest_date"],
        knowledge_graph=graph,
    )

# ─────────────────────────────────────────────
# Health check — required for Cloud Run
# ─────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok", "agent": "tenantshield-data"}

# ─────────────────────────────────────────────
# A2A Agent Card — required for A2A protocol
# ─────────────────────────────────────────────

@app.get("/.well-known/agent.json")
async def agent_card():
    """
    A2A agent card — tells the Orchestrator what this agent can do.
    The Orchestrator discovers this automatically when you give it
    the base URL of this service.
    """
    return {
        "name":        "TenantShield Data Agent",
        "description": "Looks up NYC building owner, violation history, and complaint records",
        "version":     "1.0.0",
        "endpoints": {
            "lookup": {
                "path":        "/lookup",
                "method":      "POST",
                "description": "Full building lookup — address in, owner + violations out",
                "input": {
                    "address": "string — e.g. '456 Nostrand Ave'",
                    "borough": "string — e.g. 'Brooklyn'",
                    "apartment": "string (optional)",
                },
            }
        },
    }

# ─────────────────────────────────────────────
# Run locally
# ─────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("data_agent:app", host="0.0.0.0", port=8001, reload=True)
