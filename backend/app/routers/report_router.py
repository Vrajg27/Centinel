import io
import json
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session
from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.units import cm
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle

from .. import models, auth
from ..database import get_db
from ..limiter import limiter

router = APIRouter(tags=["Reports"])

LEVEL_COLORS = {
    "Safe": colors.HexColor("#16a34a"),
    "Low": colors.HexColor("#65a30d"),
    "Medium": colors.HexColor("#d97706"),
    "High": colors.HexColor("#ea580c"),
    "Critical": colors.HexColor("#dc2626"),
}


@router.get("/report/{scan_id}")
@limiter.limit("10/minute")
def get_report(request: Request, scan_id: str, db: Session = Depends(get_db), user: models.User = Depends(auth.get_current_user)):
    scan = db.query(models.Scan).filter(
        models.Scan.id == scan_id, models.Scan.user_id == user.id
    ).first()
    if not scan:
        raise HTTPException(status_code=404, detail="Scan not found")

    buf = io.BytesIO()
    doc = SimpleDocTemplate(buf, pagesize=A4, topMargin=2 * cm, bottomMargin=2 * cm)
    styles = getSampleStyleSheet()
    title_style = ParagraphStyle("TitleX", parent=styles["Title"], textColor=colors.HexColor("#1e293b"))
    level_color = LEVEL_COLORS.get(scan.threat_level, colors.black)

    elements = [
        Paragraph("Centinel — Threat Scan Report", title_style),
        Spacer(1, 12),
        Paragraph(f"Scan Type: {scan.scan_type.upper()}", styles["Normal"]),
        Paragraph(f"Target: {scan.target_summary}", styles["Normal"]),
        Paragraph(f"Timestamp: {scan.created_at.isoformat()}", styles["Normal"]),
        Spacer(1, 16),
    ]

    summary_table = Table(
        [
            ["Risk Score", "Threat Level", "Confidence"],
            [f"{scan.risk_score}/100", scan.threat_level, f"{int(scan.confidence * 100)}%"],
        ],
        colWidths=[5.5 * cm, 5.5 * cm, 5.5 * cm],
    )
    summary_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1e293b")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("BACKGROUND", (1, 1), (1, 1), level_color),
        ("TEXTCOLOR", (1, 1), (1, 1), colors.white),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("FONTSIZE", (0, 0), (-1, -1), 11),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
    ]))
    elements += [summary_table, Spacer(1, 18)]

    elements.append(Paragraph("Explanation", styles["Heading2"]))
    elements.append(Paragraph(scan.explanation, styles["Normal"]))
    elements.append(Spacer(1, 12))

    elements.append(Paragraph("Indicators", styles["Heading2"]))
    for ind in json.loads(scan.indicators):
        elements.append(Paragraph(f"• {ind}", styles["Normal"]))
    elements.append(Spacer(1, 12))

    elements.append(Paragraph("Recommended Actions", styles["Heading2"]))
    for rec in json.loads(scan.recommendations):
        elements.append(Paragraph(f"• {rec}", styles["Normal"]))

    doc.build(elements)
    buf.seek(0)
    return StreamingResponse(
        buf, media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename=centinel_report_{scan.id}.pdf"},
    )
