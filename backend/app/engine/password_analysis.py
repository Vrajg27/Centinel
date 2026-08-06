import math
import re

COMMON_PASSWORDS = {
    "123456", "password", "123456789", "12345678", "12345", "qwerty", "abc123",
    "111111", "123123", "letmein", "welcome", "monkey", "iloveyou", "admin",
    "password1", "1234567", "sunshine", "princess", "football", "dragon",
}


def _entropy_bits(password: str) -> float:
    pool = 0
    if re.search(r"[a-z]", password):
        pool += 26
    if re.search(r"[A-Z]", password):
        pool += 26
    if re.search(r"\d", password):
        pool += 10
    if re.search(r"[^\w\s]", password):
        pool += 32
    if pool == 0:
        return 0.0
    return round(len(password) * math.log2(pool), 1)


def analyze_password(password: str) -> dict:
    """
    Analyzes password strength locally. The plaintext password is NEVER
    stored or logged — only this derived report leaves this function.
    """
    length = len(password)
    entropy = _entropy_bits(password)
    lower_pw = password.lower()

    is_common = lower_pw in COMMON_PASSWORDS
    has_sequential = bool(re.search(r"(0123|1234|2345|3456|4567|5678|6789|abcd|bcde|cdef|qwer|asdf)", lower_pw))
    has_repeats = bool(re.search(r"(.)\1{2,}", password))
    dictionary_risk = is_common or any(w in lower_pw for w in ["password", "qwerty", "admin", "letmein"])

    # Score 0-100 (higher = stronger)
    score = 0
    score += min(length * 4, 40)
    score += min(entropy * 0.6, 40)
    if re.search(r"[A-Z]", password):
        score += 5
    if re.search(r"\d", password):
        score += 5
    if re.search(r"[^\w\s]", password):
        score += 10
    if dictionary_risk:
        score -= 40
    if has_sequential:
        score -= 15
    if has_repeats:
        score -= 10
    if length < 8:
        score -= 20
    score = max(0, min(100, round(score)))

    if score >= 80:
        strength = "Very Strong"
    elif score >= 60:
        strength = "Strong"
    elif score >= 40:
        strength = "Moderate"
    elif score >= 20:
        strength = "Weak"
    else:
        strength = "Very Weak"

    recommendations = []
    if length < 12:
        recommendations.append("Use at least 12 characters.")
    if not re.search(r"[A-Z]", password):
        recommendations.append("Add uppercase letters.")
    if not re.search(r"\d", password):
        recommendations.append("Add numbers.")
    if not re.search(r"[^\w\s]", password):
        recommendations.append("Add special characters (!@#$...).")
    if dictionary_risk:
        recommendations.append("Avoid common passwords or dictionary words.")
    if has_sequential:
        recommendations.append("Avoid sequential character patterns (abcd, 1234, qwer).")
    if has_repeats:
        recommendations.append("Avoid repeating the same character multiple times in a row.")
    if not recommendations:
        recommendations.append("This password looks strong. Still, use a unique password per site and a password manager.")

    explanation = (
        f"This password is rated {strength} ({score}/100) with an estimated entropy of {entropy} bits. "
    )
    if dictionary_risk:
        explanation += "It matches or closely resembles a commonly used/leaked password, making it highly guessable. "
    if has_sequential:
        explanation += "It contains a sequential character pattern, which is easy for automated tools to guess. "
    if has_repeats:
        explanation += "It contains repeated characters that reduce randomness. "

    return {
        "length": length,
        "entropy_bits": entropy,
        "score": score,
        "strength": strength,
        "is_common_password": is_common,
        "has_sequential_pattern": has_sequential,
        "has_repeated_chars": has_repeats,
        "dictionary_risk": dictionary_risk,
        "recommendations": recommendations,
        "explanation": explanation.strip(),
        # risk score is inverse of strength, to keep the same shape as other scans
        "risk_score": 100 - score,
    }
