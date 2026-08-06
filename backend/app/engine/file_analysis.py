import hashlib
import zipfile
import io
import os

from .risk_engine import Signal, combine_signals, RiskAssessment

DANGEROUS_EXTENSIONS = {".exe", ".scr", ".bat", ".cmd", ".vbs", ".js", ".jar", ".msi", ".apk", ".dll", ".ps1"}
MACRO_ENABLED_EXT = {".docm", ".xlsm", ".pptm"}

# A tiny illustrative local blocklist of known-bad hashes (EICAR test file etc.)
# Swap for a real VirusTotal hash lookup by filling config.VIRUSTOTAL_API_KEY.
KNOWN_BAD_SHA256 = {
    "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0": "EICAR-Test-File (test signature, not real malware)",
}


def _hashes(data: bytes):
    return {
        "md5": hashlib.md5(data).hexdigest(),
        "sha1": hashlib.sha1(data).hexdigest(),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def analyze_file(filename: str, data: bytes):
    signals = []
    ext = os.path.splitext(filename)[1].lower()
    size = len(data)
    hashes = _hashes(data)

    info = {"filename": filename, "size_bytes": size, **hashes}

    if hashes["sha256"] in KNOWN_BAD_SHA256:
        signals.append(Signal(
            "known_malicious_hash",
            f"the file's hash matches a known-bad signature: {KNOWN_BAD_SHA256[hashes['sha256']]}",
            80, "technical",
        ))

    if ext in DANGEROUS_EXTENSIONS:
        signals.append(Signal(
            "dangerous_extension",
            f"the file extension '{ext}' is directly executable and a common malware delivery format",
            30, "technical",
        ))

    if ext in MACRO_ENABLED_EXT:
        signals.append(Signal(
            "macro_enabled_format",
            f"the file format '{ext}' supports embedded macros, a common malware technique",
            18, "technical",
        ))

    # Double extension trick, e.g. invoice.pdf.exe
    parts = filename.lower().split(".")
    if len(parts) > 2 and parts[-1] in {"exe", "scr", "js", "vbs", "bat"}:
        signals.append(Signal(
            "double_extension",
            f"the filename uses a double extension ('{filename}') to disguise an executable as a document",
            35, "technical",
        ))

    # ZIP / APK / DOCX-family inspection (all are ZIP containers)
    if ext in {".zip", ".apk", ".docx", ".xlsx", ".pptx", ".docm", ".xlsm"}:
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as zf:
                names = zf.namelist()
                if any(n.lower().startswith("word/vbaproject") or n.lower().endswith(".bin") for n in names):
                    signals.append(Signal(
                        "embedded_macro_project",
                        "the archive contains an embedded VBA macro project, a common malware payload location",
                        25, "technical",
                    ))
                exe_inside = [n for n in names if os.path.splitext(n)[1].lower() in DANGEROUS_EXTENSIONS]
                if exe_inside:
                    signals.append(Signal(
                        "executable_inside_archive",
                        f"the archive contains executable file(s) inside it ({exe_inside[0]})",
                        30, "technical",
                    ))
                if ext == ".apk":
                    if "AndroidManifest.xml" not in names:
                        signals.append(Signal(
                            "malformed_apk",
                            "the file has an .apk extension but is missing AndroidManifest.xml, suggesting it is malformed or repackaged",
                            20, "technical",
                        ))
        except zipfile.BadZipFile:
            signals.append(Signal(
                "corrupt_container",
                "the file claims a container format (zip/docx/apk) but is not a valid archive, which is suspicious",
                20, "technical",
            ))

    if size == 0:
        signals.append(Signal("empty_file", "the file is empty (0 bytes)", 5, "technical"))

    assessment = combine_signals(signals, base_confidence=0.55, subject_label=f"the file '{filename}'")
    return assessment, info
