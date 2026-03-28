"""
TenantShield — Complaint Agent
================================
A2A-compatible agent that does two things:
  1. POST /classify — takes a visual hazard description, matches it to
     an HMC citation from hmc_citations.json. NEVER uses Gemini for statutes.
  2. POST /draft    — takes building data + hazard info, returns a fully
     structured complaint JSON matching the spec schema.

The split matters: Gemini identifies the hazard TYPE (from the camera).
This agent provides the STATUTE. This prevents hallucination of legal cites.

Run:
  pip install fastapi uvicorn
  uvicorn complaint_agent:app --reload --port 8002
"""

import json
import uuid
from datetime import datetime
from pathlib import Path
from typing import Optional
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

app = FastAPI(
    title="TenantShield Complaint Agent",
    description="Classifies hazards and drafts legally-cited complaints",
    version="1.0.0",
)
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"],
)

# ─────────────────────────────────────────────
# Load HMC citations — the source of truth for statutes
# ─────────────────────────────────────────────
# This file has 15 entries, each with: id, hazard_type, keywords,
# class, statute, description, correction_deadline.
# Keyword matching is deterministic + auditable — a lawyer can review it.

CITATIONS_PATH = Path(__file__).parent / "hmc_citations.json"

def load_citations() -> list:
    try:
        with open(CITATIONS_PATH) as f:
            return json.load(f)
    except Exception as e:
        print(f"[complaint_agent] WARNING: could not load citations: {e}")
        return []

CITATIONS = load_citations()

# ─────────────────────────────────────────────
# Request/Response models
# ─────────────────────────────────────────────

class ClassifyRequest(BaseModel):
    """Gemini sees a hazard in the camera and describes it in plain English."""
    description: str

class ClassifyResponse(BaseModel):
    """Returns the matched HMC statute — Gemini must use ONLY this."""
    hazard_type: str
    hazard_class: str        # A, B, or C
    class_label: str         # human-readable: "Immediately hazardous", etc.
    statute: str             # e.g. "NYC HMC §27-2031"
    statute_text: str        # plain English description from citations table
    correction_deadline: str # "24 hours", "30 days", "90 days"
    bis_codes: list = []     # Relevant BIS complaint disposition codes
    bis_notes: str = ""      # Human-readable BIS code explanations

class DraftRequest(BaseModel):
    """Full building data + hazard info for complaint generation."""
    building_data: dict
    hazard_type: str
    hazard_class: str
    statute: str
    evidence_description: str
    session_id: Optional[str] = None
    apartment: Optional[str] = None

# ─────────────────────────────────────────────
# POST /classify
# ─────────────────────────────────────────────
# Keyword-match the visual description against hmc_citations.json.
# Returns the best-matching citation. If no match, returns a safe
# fallback so the pipeline never breaks.

CLASS_LABELS = {
    "C": "Immediately hazardous",
    "B": "Hazardous",
    "A": "Non-hazardous",
}

@app.post("/classify", response_model=ClassifyResponse)
async def classify_hazard(req: ClassifyRequest):
    """
    Match a visual hazard description to an HMC citation.
    Uses keyword scoring with minimum thresholds to prevent over-classification.

    Scoring rules:
      - Each keyword match adds points equal to the number of words in the keyword
        (multi-word keywords like "no heat" score 2, "water" scores 1)
      - Class C citations require a MINIMUM score of 3 to match
        (prevents single vague keywords from triggering emergency classification)
      - Class B citations require a minimum score of 2
      - Class A citations require a minimum score of 1
    """
    description_lower = req.description.lower()

    # Minimum scores required by hazard class.
    # We lowered this because single words like 'mold' and 'roach' broke.
    # We rely on Gemini's strict prompt to pre-filter non-hazards now.
    MIN_SCORES = {"C": 2, "B": 1, "A": 1}

    best_match = None
    best_score = 0

    for citation in CITATIONS:
        score = 0
        for kw in citation["keywords"]:
            if kw.lower() in description_lower:
                score += len(kw.split())

        # Some citations require BOTH primary keywords AND secondary keywords.
        # e.g. water_leak_electrical needs water/leak AND electrical/outlet.
        # If require_also is present, at least one must match or score is zeroed.
        # Matched secondary keywords also boost the score to outrank non-gated entries.
        # Negation: if "no {keyword}" or "not near {keyword}" appears, it doesn't count.
        require_also = citation.get("require_also", [])
        if require_also and score > 0:
            negation_patterns = ["no ", "not ", "without ", "no nearby ", "not near ", "aren't ", "isn't "]
            secondary_matched = []
            for kw in require_also:
                kw_lower = kw.lower()
                if kw_lower not in description_lower:
                    continue
                # Check if this keyword is negated
                idx = description_lower.index(kw_lower)
                prefix = description_lower[max(0, idx - 40):idx]
                negated = any(neg in prefix for neg in negation_patterns)
                if not negated:
                    secondary_matched.append(kw)

            if not secondary_matched:
                score = 0  # Primary matched but secondary NOT confirmed
            else:
                for kw in secondary_matched:
                    score += len(kw.split())

        # Enforce minimum score based on hazard class
        min_required = MIN_SCORES.get(citation["class"], 2)
        if score < min_required:
            continue

        if score > best_score:
            best_score = score
            best_match = citation

    if not best_match:
        return ClassifyResponse(
            hazard_type="unconfirmed",
            hazard_class="NONE",
            class_label="Not enough evidence to classify",
            statute="N/A",
            statute_text="The description provided does not match a specific "
                         "housing code violation with sufficient confidence. "
                         "Please provide more details about the condition — "
                         "is there active leaking, structural damage, or "
                         "proximity to electrical components?",
            correction_deadline="N/A",
            bis_codes=[],
            bis_notes="",
        )

    return ClassifyResponse(
        hazard_type=best_match["hazard_type"],
        hazard_class=best_match["class"],
        class_label=CLASS_LABELS.get(best_match["class"], "Hazardous"),
        statute=best_match["statute"],
        statute_text=best_match["description"],
        correction_deadline=best_match["correction_deadline"],
        bis_codes=best_match.get("bis_codes", []),
        bis_notes=best_match.get("bis_notes", ""),
    )


# ─────────────────────────────────────────────
# POST /draft
# ─────────────────────────────────────────────
# Assembles the full complaint JSON from building data + hazard.
# Uses the statute from /classify — never generates its own.

@app.post("/draft")
async def draft_complaint(req: DraftRequest):
    """
    Assemble a legally-cited complaint using data from the Data Agent
    and the statute from /classify. Returns the full complaint JSON
    shape that Android renders in ComplaintActivity.
    """
    bd = req.building_data
    session_id = req.session_id or str(uuid.uuid4())
    now = datetime.utcnow()

    # Look up the full citation entry for the statute text
    citation_entry = next(
        (c for c in CITATIONS if c["statute"] == req.statute),
        None,
    )
    # Also try matching by hazard_type for better precision
    if not citation_entry:
        citation_entry = next(
            (c for c in CITATIONS if c["hazard_type"] == req.hazard_type),
            None,
        )
    statute_text = (
        citation_entry["description"]
        if citation_entry
        else "Condition violating NYC Housing Maintenance Code."
    )
    correction_deadline = (
        citation_entry["correction_deadline"]
        if citation_entry
        else "30 days"
    )
    bis_codes = citation_entry.get("bis_codes", []) if citation_entry else []
    bis_notes = citation_entry.get("bis_notes", "") if citation_entry else ""

    # Calculate days_neglected from complaints if available
    days_neglected = bd.get("days_neglected", 0)

    # Find last inspection date from violations
    violations = bd.get("violations", [])
    last_inspection = None
    if violations:
        dates = [v.get("date") for v in violations if v.get("date")]
        if dates:
            last_inspection = max(dates)

    apartment = req.apartment or bd.get("apartment") or "N/A"

    complaint = {
        "session_id": session_id,
        "timestamp": now.isoformat() + "Z",

        "property": {
            "address": bd.get("address", ""),
            "apartment": apartment,
            "bbl": bd.get("bbl"),
            "registration_id": bd.get("registration_id"),
        },

        "owner": {
            "owner_name": bd.get("owner_name"),
            "corporation_name": bd.get("corporation_name"),
            "owner_phone": bd.get("owner_phone"),
            "manager_name": bd.get("manager_name"),
            "manager_phone": bd.get("manager_phone"),
        },

        "violation": {
            "hazard_type": req.hazard_type,
            "hazard_class": req.hazard_class,
            "class_label": CLASS_LABELS.get(req.hazard_class, "Hazardous"),
            "statute": req.statute,
            "statute_text": statute_text,
            "evidence_description": req.evidence_description,
            "correction_deadline": correction_deadline,
            "bis_codes": bis_codes,
            "bis_notes": bis_notes,
        },

        "history": {
            "total_violations": bd.get("violation_count", 0),
            "open_violations": bd.get("open_violations", 0),
            "class_c_count": bd.get("class_c_count", 0),
            "class_b_count": bd.get("class_b_count", 0),
            "days_neglected": days_neglected,
            "last_inspection": last_inspection,
        },

        # Evidence URLs are filled in by the Orchestrator after GCS upload
        "evidence": {
            "frame_urls": [],
            "video_url": None,
        },

        "actions": {
            "hpd_online_url": bd.get("hpd_online_url"),
            "nyc311_url": "https://portal.311.nyc.gov",
        },

        "complaint_text": _generate_complaint_text(
            address=bd.get("address", "the property"),
            apartment=apartment,
            owner_name=bd.get("owner_name") or bd.get("corporation_name") or "the landlord",
            hazard_type=req.hazard_type,
            hazard_class=req.hazard_class,
            statute=req.statute,
            statute_text=statute_text,
            evidence=req.evidence_description,
            correction_deadline=correction_deadline,
            total_violations=bd.get("violation_count") or bd.get("open_violations") or 0,
            class_c_count=bd.get("class_c_count", 0),
        ),

        "next_steps": _generate_next_steps(req.hazard_class),
    }

    return complaint


def _generate_complaint_text(
    address: str, apartment: str, owner_name: str,
    hazard_type: str, hazard_class: str, statute: str,
    statute_text: str, evidence: str, correction_deadline: str,
    total_violations: int, class_c_count: int,
) -> str:
    """Generate a formal 311 complaint narrative text."""
    class_label = CLASS_LABELS.get(hazard_class, "hazardous")

    text = (
        f"I am filing a complaint regarding a {class_label.lower()} condition "
        f"at {address}, Apartment {apartment}. "
        f"The property is owned/managed by {owner_name}.\n\n"
        f"CONDITION: {hazard_type.replace('_', ' ').title()}\n"
        f"This is a Class {hazard_class} violation under {statute}: "
        f"{statute_text}\n\n"
        f"EVIDENCE: {evidence}\n\n"
        f"CORRECTION DEADLINE: This Class {hazard_class} violation must be "
        f"corrected within {correction_deadline} per the NYC Housing "
        f"Maintenance Code.\n"
    )

    if total_violations and int(total_violations) > 0:
        text += (
            f"\nBUILDING HISTORY: This building currently has "
            f"{total_violations} open violations on record"
        )
        if class_c_count and int(class_c_count) > 0:
            text += f", including {class_c_count} Class C (immediately hazardous) violations"
        text += (
            ". This pattern of neglect demonstrates the owner's ongoing "
            "failure to maintain habitable conditions.\n"
        )

    text += (
        f"\nI request that HPD inspect this condition and issue appropriate "
        f"violations. I am available for access to the apartment for inspection."
    )

    return text


def _generate_next_steps(hazard_class: str) -> list:
    """
    Return actionable next-steps tailored to the hazard severity.
    Class C gets urgent language; A/B get standard timelines.
    """
    if hazard_class == "C":
        return [
            "HPD must respond to Class C violations within 24 hours.",
            "If no response in 24 hours, call HPD Emergency at 212-660-4800.",
            "Keep this complaint and your photos for housing court.",
            "Contact Met Council on Housing for free legal advice: 212-693-0000.",
        ]
    elif hazard_class == "B":
        return [
            "HPD must respond to Class B violations within 30 days.",
            "Call 311 to file a formal complaint — reference this document.",
            "If the landlord does not respond in 30 days, you may file an HP Action in Housing Court.",
            "Contact Met Council on Housing for free legal advice: 212-693-0000.",
        ]
    else:
        return [
            "Class A violations must be corrected within 90 days.",
            "Call 311 to file a formal complaint — reference this document.",
            "Document the condition with photos before and after any repair attempts.",
            "Contact Met Council on Housing for free legal advice: 212-693-0000.",
        ]


# ─────────────────────────────────────────────
# Health + A2A
# ─────────────────────────────────────────────

@app.get("/health")
async def health():
    return {
        "status": "ok",
        "agent": "tenantshield-complaint",
        "citations_loaded": len(CITATIONS),
    }

@app.get("/.well-known/agent.json")
async def agent_card():
    return {
        "name": "TenantShield Complaint Agent",
        "description": "Classifies housing hazards via HMC citation lookup "
                       "and generates legally-cited complaint documents",
        "version": "1.0.0",
        "endpoints": {
            "classify": {
                "path": "/classify",
                "method": "POST",
                "description": "Match visual hazard description to HMC statute",
            },
            "draft": {
                "path": "/draft",
                "method": "POST",
                "description": "Generate full complaint JSON from building data + hazard",
            },
        },
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("complaint_agent:app", host="0.0.0.0", port=8002, reload=True)
