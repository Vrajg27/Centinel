"""
Promotes an existing user to admin directly via the database — useful for
bootstrapping your very first admin if you didn't set CENTINEL_ADMIN_EMAILS
before they registered (that env var only applies at registration time).

Usage (run from the backend/ directory, with the venv active):
    python scripts/make_admin.py you@example.com
"""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.database import SessionLocal
from app import models


def main():
    if len(sys.argv) != 2:
        print("Usage: python scripts/make_admin.py <email>")
        sys.exit(1)

    email = sys.argv[1].strip().lower()
    db = SessionLocal()
    try:
        user = db.query(models.User).filter(models.User.email == email).first()
        if not user:
            print(f"No user found with email '{email}'. They need to register first.")
            sys.exit(1)
        if user.is_admin:
            print(f"'{email}' is already an admin.")
            return
        user.is_admin = True
        db.commit()
        print(f"'{email}' is now an admin.")
    finally:
        db.close()


if __name__ == "__main__":
    main()
