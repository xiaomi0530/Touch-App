from __future__ import annotations

import base64
import hashlib
import hmac
import json
import mimetypes
import os
import secrets
import sqlite3
import time
import uuid
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse


HOST = "127.0.0.1"
PORT = 8000
DATA_DIR = Path(__file__).resolve().parent / "data"
DB_PATH = DATA_DIR / "touch_dev.sqlite3"
SECRET_PATH = DATA_DIR / "dev_token_secret.key"
UPLOAD_DIR = DATA_DIR / "uploads" / "avatars"
CARD_BACKGROUND_UPLOAD_DIR = DATA_DIR / "uploads" / "card-backgrounds"

ACCESS_TOKEN_TTL_SECONDS = 15 * 60
REFRESH_TOKEN_TTL_SECONDS = 30 * 24 * 60 * 60
PASSWORD_ITERATIONS = 600_000
MAX_BODY_BYTES = 16 * 1024
MAX_AVATAR_BYTES = 2 * 1024 * 1024
MAX_CARD_BACKGROUND_BYTES = 3 * 1024 * 1024
MEET_TOKEN_TTL_SECONDS = 120
DEFAULT_CARD_BACKGROUND_KEYS = {"mizuki", "muelsyse", "shu"}


def utc_now() -> int:
    return int(time.time())


def b64url_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def b64url_decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)


def json_bytes(value: Any) -> bytes:
    return json.dumps(value, separators=(",", ":"), sort_keys=True).encode("utf-8")


def load_or_create_secret() -> bytes:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    env_secret = os.environ.get("TOUCH_DEV_TOKEN_SECRET")
    if env_secret:
        return env_secret.encode("utf-8")
    if SECRET_PATH.exists():
        return SECRET_PATH.read_bytes()
    secret = secrets.token_bytes(32)
    SECRET_PATH.write_bytes(secret)
    return secret


TOKEN_SECRET = load_or_create_secret()


def issue_access_token(user_id: str) -> tuple[str, int]:
    expires_at = utc_now() + ACCESS_TOKEN_TTL_SECONDS
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": user_id,
        "iat": utc_now(),
        "exp": expires_at,
        "scope": "touch-api",
    }
    encoded_header = b64url_encode(json_bytes(header))
    encoded_payload = b64url_encode(json_bytes(payload))
    signing_input = f"{encoded_header}.{encoded_payload}".encode("ascii")
    signature = hmac.new(TOKEN_SECRET, signing_input, hashlib.sha256).digest()
    return f"{encoded_header}.{encoded_payload}.{b64url_encode(signature)}", expires_at


def verify_access_token(token: str) -> str:
    parts = token.split(".")
    if len(parts) != 3:
        raise ApiError(401, "invalid_token", "Access token format is invalid.")

    signing_input = f"{parts[0]}.{parts[1]}".encode("ascii")
    expected_signature = hmac.new(TOKEN_SECRET, signing_input, hashlib.sha256).digest()
    actual_signature = b64url_decode(parts[2])
    if not hmac.compare_digest(expected_signature, actual_signature):
        raise ApiError(401, "invalid_token", "Access token signature is invalid.")

    try:
        payload = json.loads(b64url_decode(parts[1]).decode("utf-8"))
    except (ValueError, UnicodeDecodeError) as exc:
        raise ApiError(401, "invalid_token", "Access token payload is invalid.") from exc

    if int(payload.get("exp", 0)) < utc_now():
        raise ApiError(401, "token_expired", "Access token has expired.")

    user_id = payload.get("sub")
    if not isinstance(user_id, str) or not user_id:
        raise ApiError(401, "invalid_token", "Access token subject is invalid.")
    return user_id


def hash_password(password: str, salt: bytes | None = None) -> tuple[str, str]:
    actual_salt = salt or secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac(
        "sha256",
        password.encode("utf-8"),
        actual_salt,
        PASSWORD_ITERATIONS,
    )
    return b64url_encode(actual_salt), b64url_encode(digest)


def verify_password(password: str, salt_b64: str, password_hash_b64: str) -> bool:
    salt = b64url_decode(salt_b64)
    _, candidate_hash = hash_password(password, salt=salt)
    return hmac.compare_digest(candidate_hash, password_hash_b64)


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


@dataclass(frozen=True)
class User:
    id: str
    display_name: str
    email: str
    created_at: int
    avatar_path: str | None
    bio: str | None
    birthday: str | None
    gender: str | None
    card_background_path: str | None
    card_background_key: str | None
    last_seen_at: int | None


def connect_db() -> sqlite3.Connection:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB_PATH)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys = ON")
    return db


def init_db() -> None:
    with connect_db() as db:
        db.executescript(
            """
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_salt TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                avatar_path TEXT,
                bio TEXT,
                birthday TEXT,
                gender TEXT,
                card_background_path TEXT,
                card_background_key TEXT,
                last_seen_at INTEGER
            );

            CREATE TABLE IF NOT EXISTS refresh_tokens (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                token_hash TEXT NOT NULL UNIQUE,
                expires_at INTEGER NOT NULL,
                revoked_at INTEGER
            );

            CREATE TABLE IF NOT EXISTS devices (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                device_name TEXT NOT NULL,
                public_key TEXT NOT NULL,
                key_algorithm TEXT NOT NULL,
                app_version TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                revoked_at INTEGER
            );

            CREATE TABLE IF NOT EXISTS meetings (
                id TEXT PRIMARY KEY,
                owner_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                person_user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
                person_name TEXT NOT NULL,
                met_date TEXT NOT NULL,
                met_time TEXT NOT NULL,
                status TEXT NOT NULL,
                method TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS meet_tokens (
                id TEXT PRIMARY KEY,
                token_hash TEXT NOT NULL UNIQUE,
                owner_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                issued_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                used_at INTEGER
            );

            CREATE TABLE IF NOT EXISTS meeting_proof_submissions (
                id TEXT PRIMARY KEY,
                scanner_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                client_submission_id TEXT NOT NULL,
                token_id TEXT NOT NULL REFERENCES meet_tokens(id),
                scanner_meeting_id TEXT NOT NULL REFERENCES meetings(id),
                scanned_meeting_id TEXT NOT NULL REFERENCES meetings(id),
                created_at INTEGER NOT NULL,
                UNIQUE(scanner_user_id, client_submission_id)
            );

            CREATE TABLE IF NOT EXISTS friendships (
                id TEXT PRIMARY KEY,
                requester_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                addressee_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                status TEXT NOT NULL,
                requester_remark TEXT,
                addressee_remark TEXT,
                requester_blocked_at INTEGER,
                addressee_blocked_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                UNIQUE(requester_user_id, addressee_user_id)
            );

            CREATE TABLE IF NOT EXISTS day_events (
                id TEXT PRIMARY KEY,
                owner_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                event_date TEXT NOT NULL,
                title TEXT NOT NULL,
                note TEXT NOT NULL,
                image_base64 TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS day_event_participants (
                event_id TEXT NOT NULL REFERENCES day_events(id) ON DELETE CASCADE,
                friend_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                PRIMARY KEY (event_id, friend_user_id)
            );

            CREATE TABLE IF NOT EXISTS user_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                event_type TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            """
        )
        columns = {row["name"] for row in db.execute("PRAGMA table_info(users)").fetchall()}
        if "avatar_path" not in columns:
            db.execute("ALTER TABLE users ADD COLUMN avatar_path TEXT")
        for column_name, column_type in (
            ("bio", "TEXT"),
            ("birthday", "TEXT"),
            ("gender", "TEXT"),
            ("card_background_path", "TEXT"),
            ("card_background_key", "TEXT"),
            ("last_seen_at", "INTEGER"),
        ):
            if column_name not in columns:
                db.execute(f"ALTER TABLE users ADD COLUMN {column_name} {column_type}")
        meeting_columns = {row["name"] for row in db.execute("PRAGMA table_info(meetings)").fetchall()}
        if "person_user_id" not in meeting_columns:
            db.execute("ALTER TABLE meetings ADD COLUMN person_user_id TEXT REFERENCES users(id) ON DELETE SET NULL")
        friendship_columns = {row["name"] for row in db.execute("PRAGMA table_info(friendships)").fetchall()}
        if "requester_remark" not in friendship_columns:
            db.execute("ALTER TABLE friendships ADD COLUMN requester_remark TEXT")
        if "addressee_remark" not in friendship_columns:
            db.execute("ALTER TABLE friendships ADD COLUMN addressee_remark TEXT")
        seed_local_demo_user(db)


def normalize_email(email: str) -> str:
    normalized = email.strip().lower()
    if "@" not in normalized or normalized.startswith("@") or normalized.endswith("@"):
        raise ApiError(400, "invalid_email", "Email address is invalid.")
    return normalized


def validate_password(password: str) -> None:
    if len(password) < 8:
        raise ApiError(400, "weak_password", "Password must be at least 8 characters.")


def user_from_row(row: sqlite3.Row) -> User:
    return User(
        id=row["id"],
        display_name=row["display_name"],
        email=row["email"],
        created_at=row["created_at"],
        avatar_path=row["avatar_path"],
        bio=row["bio"],
        birthday=row["birthday"],
        gender=row["gender"],
        card_background_path=row["card_background_path"],
        card_background_key=row["card_background_key"],
        last_seen_at=row["last_seen_at"],
    )


def public_user(user: User) -> dict[str, Any]:
    avatar_url = f"/uploads/avatars/{user.avatar_path}" if user.avatar_path else None
    card_background_url = (
        f"/uploads/card-backgrounds/{user.card_background_path}"
        if user.card_background_path
        else None
    )
    return {
        "id": user.id,
        "displayName": user.display_name,
        "email": user.email,
        "createdAt": user.created_at,
        "avatarUrl": avatar_url,
        "bio": user.bio,
        "birthday": user.birthday,
        "gender": user.gender,
        "cardBackgroundUrl": card_background_url,
        "cardBackgroundKey": user.card_background_key,
        "lastSeenAt": user.last_seen_at,
    }


def public_user_row(row: sqlite3.Row) -> dict[str, Any]:
    return public_user(user_from_row(row))


def display_name_is_taken(db: sqlite3.Connection, display_name: str, excluding_user_id: str | None = None) -> bool:
    normalized = display_name.strip()
    if excluding_user_id:
        row = db.execute(
            "SELECT id FROM users WHERE lower(display_name) = lower(?) AND id != ? LIMIT 1",
            (normalized, excluding_user_id),
        ).fetchone()
    else:
        row = db.execute(
            "SELECT id FROM users WHERE lower(display_name) = lower(?) LIMIT 1",
            (normalized,),
        ).fetchone()
    return row is not None


def normalize_optional_text(value: Any, max_length: int) -> str | None:
    text = str(value or "").strip()
    return text[:max_length] if text else None


def normalize_birthday(value: Any) -> str | None:
    birthday = str(value or "").strip()
    if not birthday:
        return None
    if len(birthday) != 10 or birthday[4] != "-" or birthday[7] != "-":
        raise ApiError(400, "invalid_birthday", "生日格式应为 YYYY-MM-DD。")
    try:
        year = int(birthday[0:4])
        month = int(birthday[5:7])
        day = int(birthday[8:10])
    except ValueError as exc:
        raise ApiError(400, "invalid_birthday", "生日格式应为 YYYY-MM-DD。") from exc
    if year < 1900 or year > 2100 or month < 1 or month > 12 or day < 1 or day > 31:
        raise ApiError(400, "invalid_birthday", "生日日期不合法。")
    return birthday


def normalize_gender(value: Any) -> str | None:
    gender = str(value or "").strip()
    if not gender:
        return None
    if gender not in {"男", "女", "其他", "保密"}:
        raise ApiError(400, "invalid_gender", "性别选项不合法。")
    return gender


def mark_user_seen(db: sqlite3.Connection, user_id: str, seen_at: int | None = None) -> None:
    db.execute("UPDATE users SET last_seen_at = ? WHERE id = ?", (seen_at or utc_now(), user_id))


def public_meeting(row: sqlite3.Row) -> dict[str, Any]:
    return {
        "id": row["id"],
        "personUserId": row["person_user_id"],
        "personName": row["person_name"],
        "metDate": row["met_date"],
        "metTime": row["met_time"],
        "status": row["status"],
        "method": row["method"],
        "createdAt": row["created_at"],
    }


def format_met_date_time(timestamp: int) -> tuple[str, str]:
    struct_time = time.gmtime(timestamp)
    return time.strftime("%Y-%m-%d", struct_time), time.strftime("%H:%M", struct_time)


def friendship_key(user_a: str, user_b: str) -> tuple[str, str]:
    return (user_a, user_b) if user_a < user_b else (user_b, user_a)


def get_friendship_between(db: sqlite3.Connection, user_a: str, user_b: str) -> sqlite3.Row | None:
    return db.execute(
        """
        SELECT * FROM friendships
        WHERE (requester_user_id = ? AND addressee_user_id = ?)
           OR (requester_user_id = ? AND addressee_user_id = ?)
        """,
        (user_a, user_b, user_b, user_a),
    ).fetchone()


def friendship_is_active(row: sqlite3.Row | None) -> bool:
    return (
        row is not None
        and row["status"] == "accepted"
        and row["requester_blocked_at"] is None
        and row["addressee_blocked_at"] is None
    )


def public_friendship(db: sqlite3.Connection, row: sqlite3.Row, viewer_user_id: str) -> dict[str, Any]:
    friend_user_id = row["addressee_user_id"] if row["requester_user_id"] == viewer_user_id else row["requester_user_id"]
    user_row = db.execute("SELECT * FROM users WHERE id = ?", (friend_user_id,)).fetchone()
    blocked_by_me = (
        row["requester_blocked_at"] is not None
        if row["requester_user_id"] == viewer_user_id
        else row["addressee_blocked_at"] is not None
    )
    blocked_me = (
        row["addressee_blocked_at"] is not None
        if row["requester_user_id"] == viewer_user_id
        else row["requester_blocked_at"] is not None
    )
    remark = (
        row["requester_remark"]
        if row["requester_user_id"] == viewer_user_id
        else row["addressee_remark"]
    )
    return {
        "id": row["id"],
        "status": row["status"],
        "direction": "outgoing" if row["requester_user_id"] == viewer_user_id else "incoming",
        "friend": public_user_row(user_row) if user_row else None,
        "remark": remark,
        "blockedByMe": blocked_by_me,
        "blockedMe": blocked_me,
        "createdAt": row["created_at"],
        "updatedAt": row["updated_at"],
    }


def public_day_event(db: sqlite3.Connection, row: sqlite3.Row) -> dict[str, Any]:
    participant_rows = db.execute(
        """
        SELECT users.*
        FROM day_event_participants
        JOIN users ON users.id = day_event_participants.friend_user_id
        WHERE day_event_participants.event_id = ?
        ORDER BY users.display_name ASC
        """,
        (row["id"],),
    ).fetchall()
    images = parse_event_images(row["image_base64"])
    return {
        "id": row["id"],
        "eventDate": row["event_date"],
        "title": row["title"],
        "note": row["note"],
        "imageBase64": images[0] if images else None,
        "imageBase64List": images,
        "participants": [public_user_row(participant) for participant in participant_rows],
        "createdAt": row["created_at"],
        "updatedAt": row["updated_at"],
    }


def create_user_event(
    db: sqlite3.Connection,
    user_id: str,
    event_type: str,
    payload: dict[str, Any],
    created_at: int | None = None,
) -> None:
    db.execute(
        """
        INSERT INTO user_events (user_id, event_type, payload_json, created_at)
        VALUES (?, ?, ?, ?)
        """,
        (user_id, event_type, json.dumps(payload, ensure_ascii=False), created_at or utc_now()),
    )


def public_user_event(row: sqlite3.Row) -> dict[str, Any]:
    try:
        payload = json.loads(row["payload_json"])
    except json.JSONDecodeError:
        payload = {}
    return {
        "id": row["id"],
        "type": row["event_type"],
        "payload": payload if isinstance(payload, dict) else {},
        "createdAt": row["created_at"],
    }


def active_friend_ids(db: sqlite3.Connection, user_id: str) -> set[str]:
    rows = db.execute(
        """
        SELECT requester_user_id, addressee_user_id
        FROM friendships
        WHERE status = 'accepted'
          AND requester_blocked_at IS NULL
          AND addressee_blocked_at IS NULL
          AND (requester_user_id = ? OR addressee_user_id = ?)
        """,
        (user_id, user_id),
    ).fetchall()
    return {
        row["addressee_user_id"] if row["requester_user_id"] == user_id else row["requester_user_id"]
        for row in rows
    }


def accepted_friend_ids(db: sqlite3.Connection, user_id: str) -> set[str]:
    rows = db.execute(
        """
        SELECT requester_user_id, addressee_user_id
        FROM friendships
        WHERE status = 'accepted'
          AND (requester_user_id = ? OR addressee_user_id = ?)
        """,
        (user_id, user_id),
    ).fetchall()
    return {
        row["addressee_user_id"] if row["requester_user_id"] == user_id else row["requester_user_id"]
        for row in rows
    }


def public_day_event_for_viewer(db: sqlite3.Connection, row: sqlite3.Row, viewer_user_id: str) -> dict[str, Any] | None:
    visible_ids = accepted_friend_ids(db, viewer_user_id)
    payload = public_day_event(db, row)
    payload["participants"] = [
        participant for participant in payload["participants"]
        if participant["id"] in visible_ids
    ]
    if not payload["participants"]:
        return None
    return payload


def parse_event_images(raw: str | None) -> list[str]:
    if not raw:
        return []
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        return [raw]
    if isinstance(parsed, list):
        return [str(value).strip() for value in parsed if str(value).strip()]
    if isinstance(parsed, str) and parsed.strip():
        return [parsed.strip()]
    return []


def seed_local_demo_user(db: sqlite3.Connection) -> None:
    email = "test@163.com"
    display_name = "Shinochanwww"
    password = "12345678"
    now = utc_now()

    def upsert_demo_user(
        demo_user_id: str,
        demo_display_name: str,
        demo_email: str,
        bio: str,
        birthday: str,
        gender: str,
        background_key: str,
    ) -> None:
        existing = db.execute("SELECT id FROM users WHERE id = ? OR email = ?", (demo_user_id, demo_email)).fetchone()
        salt, password_hash = hash_password("12345678")
        if existing is None:
            db.execute(
                """
                INSERT INTO users (
                    id, display_name, email, password_salt, password_hash, created_at,
                    avatar_path, bio, birthday, gender, card_background_path, card_background_key, last_seen_at
                )
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, NULL, ?, ?)
                """,
                (demo_user_id, demo_display_name, demo_email, salt, password_hash, now, bio, birthday, gender, background_key, now),
            )
            return
        db.execute(
            """
            UPDATE users
            SET display_name = ?, email = ?, password_salt = ?, password_hash = ?,
                bio = ?, birthday = ?, gender = ?, card_background_key = ?,
                card_background_path = NULL, last_seen_at = ?
            WHERE id = ?
            """,
            (demo_display_name, demo_email, salt, password_hash, bio, birthday, gender, background_key, now, existing["id"]),
        )

    def upsert_demo_friendship(owner_user_id: str, friend_user_id: str) -> None:
        existing = get_friendship_between(db, owner_user_id, friend_user_id)
        if existing is None:
            db.execute(
                """
                INSERT INTO friendships
                (id, requester_user_id, addressee_user_id, status, requester_blocked_at, addressee_blocked_at, created_at, updated_at)
                VALUES (?, ?, ?, 'accepted', NULL, NULL, ?, ?)
                """,
                (f"fr_demo_{friend_user_id}", owner_user_id, friend_user_id, now, now),
            )
            return
        db.execute(
            """
            UPDATE friendships
            SET requester_user_id = ?, addressee_user_id = ?, status = 'accepted',
                requester_blocked_at = NULL, addressee_blocked_at = NULL, updated_at = ?
            WHERE id = ?
            """,
            (owner_user_id, friend_user_id, now, existing["id"]),
        )
    row = db.execute("SELECT id FROM users WHERE email = ?", (email,)).fetchone()
    if row is None:
        user_id = "user_test_shinochanwww"
        salt, password_hash = hash_password(password)
        db.execute(
            """
            INSERT INTO users (
                id, display_name, email, password_salt, password_hash, created_at,
                avatar_path, bio, birthday, gender, card_background_path, card_background_key, last_seen_at
            )
            VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, NULL, 'mizuki', ?)
            """,
            (user_id, display_name, email, salt, password_hash, now, "正在把每一次碰面认真收藏起来。", "2003-05-30", "保密", now),
        )
    else:
        user_id = row["id"]
        salt, password_hash = hash_password(password)
        db.execute(
            """
            UPDATE users
            SET display_name = ?, password_salt = ?, password_hash = ?,
                bio = ?, birthday = ?, gender = ?, card_background_key = COALESCE(card_background_key, 'mizuki'),
                last_seen_at = ?
            WHERE id = ?
            """,
            (display_name, salt, password_hash, "正在把每一次碰面认真收藏起来。", "2003-05-30", "保密", now, user_id),
        )

    db.execute(
        """
        UPDATE friendships
        SET status = 'removed', updated_at = ?
        WHERE (requester_user_id = ? OR addressee_user_id = ?)
          AND (
              requester_user_id LIKE 'user_demo_%'
              OR addressee_user_id LIKE 'user_demo_%'
          )
        """,
        (now, user_id, user_id),
    )
    db.execute("DELETE FROM meetings WHERE id LIKE 'meet_demo_%'")

    demo_meetings = [
        ("meet_demo_2026_06_03_chen", "陈林", "2026-06-03", "10:20"),
        ("meet_demo_2026_06_05_xu", "徐雅", "2026-06-05", "18:05"),
        ("meet_demo_2026_06_07_wang", "王安", "2026-06-07", "14:35"),
        ("meet_demo_2026_06_10_chen", "陈林", "2026-06-10", "09:42"),
        ("meet_demo_2026_06_12_chen", "陈林", "2026-06-12", "16:10"),
        ("meet_demo_2026_06_14_xu", "徐雅", "2026-06-14", "20:15"),
        ("meet_demo_2026_06_16_wang", "王安", "2026-06-16", "18:05"),
        ("meet_demo_2026_06_17_chen", "陈林", "2026-06-17", "09:42"),
        ("meet_demo_2026_06_17_xu", "徐雅", "2026-06-17", "13:18"),
        ("meet_demo_2026_06_18_chen", "陈林", "2026-06-18", "11:06"),
        ("meet_demo_2026_06_18_wang", "王安", "2026-06-18", "19:30"),
        ("meet_demo_2026_07_02_xu", "徐雅", "2026-07-02", "15:10"),
        ("meet_demo_2026_07_11_chen", "陈林", "2026-07-11", "12:25"),
        ("meet_demo_2026_08_22_wang", "王安", "2026-08-22", "17:55"),
        ("meet_demo_2026_11_11_chen", "陈林", "2026-11-11", "19:40"),
    ]
    for meeting_id, person_name, met_date, met_time in demo_meetings:
        db.execute(
            """
            INSERT OR REPLACE INTO meetings
            (id, owner_user_id, person_name, met_date, met_time, status, method, created_at)
            VALUES (?, ?, ?, ?, ?, 'confirmed', 'NFC_HCE', ?)
            """,
            (meeting_id, user_id, person_name, met_date, met_time, now),
        )

    demo_friends = [
        ("user_demo_chen_lin", "陈林", "chenlin.demo@touch.local", "喜欢把城市里的偶遇写进手账。", "2002-02-14", "女", "muelsyse"),
        ("user_demo_xu_ya", "徐雅", "xuya.demo@touch.local", "周末常在展览、咖啡店和图书馆之间移动。", "2001-09-03", "女", "shu"),
        ("user_demo_wang_an", "王安", "wangan.demo@touch.local", "电子设备和现场音乐爱好者，碰面频率很高。", "2000-12-22", "男", "mizuki"),
    ]
    for friend_id, friend_name, friend_email, bio, birthday, gender, background_key in demo_friends:
        upsert_demo_user(friend_id, friend_name, friend_email, bio, birthday, gender, background_key)
        upsert_demo_friendship(user_id, friend_id)

    demo_meeting_friends = {
        "meet_demo_2026_06_03_chen": ("user_demo_chen_lin", "陈林"),
        "meet_demo_2026_06_05_xu": ("user_demo_xu_ya", "徐雅"),
        "meet_demo_2026_06_07_wang": ("user_demo_wang_an", "王安"),
        "meet_demo_2026_06_10_chen": ("user_demo_chen_lin", "陈林"),
        "meet_demo_2026_06_12_chen": ("user_demo_chen_lin", "陈林"),
        "meet_demo_2026_06_14_xu": ("user_demo_xu_ya", "徐雅"),
        "meet_demo_2026_06_16_wang": ("user_demo_wang_an", "王安"),
        "meet_demo_2026_06_17_chen": ("user_demo_chen_lin", "陈林"),
        "meet_demo_2026_06_17_xu": ("user_demo_xu_ya", "徐雅"),
        "meet_demo_2026_06_18_chen": ("user_demo_chen_lin", "陈林"),
        "meet_demo_2026_06_18_wang": ("user_demo_wang_an", "王安"),
        "meet_demo_2026_07_02_xu": ("user_demo_xu_ya", "徐雅"),
        "meet_demo_2026_07_11_chen": ("user_demo_chen_lin", "陈林"),
        "meet_demo_2026_08_22_wang": ("user_demo_wang_an", "王安"),
        "meet_demo_2026_11_11_chen": ("user_demo_chen_lin", "陈林"),
    }
    for meeting_id, (friend_id, friend_name) in demo_meeting_friends.items():
        db.execute(
            """
            UPDATE meetings
            SET person_user_id = ?, person_name = ?
            WHERE id = ? AND owner_user_id = ?
            """,
            (friend_id, friend_name, meeting_id, user_id),
        )

        owner_record = db.execute("SELECT * FROM meetings WHERE id = ?", (meeting_id,)).fetchone()
        if owner_record is not None:
            db.execute(
                """
                INSERT OR REPLACE INTO meetings
                (id, owner_user_id, person_user_id, person_name, met_date, met_time, status, method, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'confirmed', 'NFC_HCE', ?)
                """,
                (
                    f"{meeting_id}_peer",
                    friend_id,
                    user_id,
                    display_name,
                    owner_record["met_date"],
                    owner_record["met_time"],
                    now,
                ),
            )


def create_session(db: sqlite3.Connection, user_id: str) -> dict[str, Any]:
    access_token, access_expires_at = issue_access_token(user_id)
    refresh_token = f"rt_{secrets.token_urlsafe(48)}"
    refresh_id = f"rtok_{uuid.uuid4().hex}"
    now = utc_now()
    db.execute(
        """
        INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked_at)
        VALUES (?, ?, ?, ?, NULL)
        """,
        (
            refresh_id,
            user_id,
            hash_token(refresh_token),
            now + REFRESH_TOKEN_TTL_SECONDS,
        ),
    )
    return {
        "accessToken": access_token,
        "accessTokenExpiresAt": access_expires_at,
        "refreshToken": refresh_token,
        "refreshTokenExpiresAt": now + REFRESH_TOKEN_TTL_SECONDS,
    }


def get_authenticated_user_id(headers: dict[str, str]) -> str:
    auth_header = headers.get("authorization", "")
    prefix = "Bearer "
    if not auth_header.startswith(prefix):
        raise ApiError(401, "missing_authorization", "Authorization bearer token is required.")
    return verify_access_token(auth_header[len(prefix) :].strip())


def parse_multipart_file(raw_body: bytes, boundary: str, field_name: str) -> tuple[bytes, str]:
    marker = f"--{boundary}".encode("utf-8")
    for part in raw_body.split(marker):
        if not part or part in (b"--\r\n", b"--"):
            continue
        part = part.strip(b"\r\n")
        if b"\r\n\r\n" not in part:
            continue
        raw_headers, content = part.split(b"\r\n\r\n", 1)
        headers = raw_headers.decode("utf-8", errors="replace")
        if f'name="{field_name}"' not in headers:
            continue
        filename = "avatar.bin"
        for section in headers.split(";"):
            section = section.strip()
            if section.startswith("filename="):
                filename = section.split("=", 1)[1].strip().strip('"') or filename
        return content.rstrip(b"\r\n"), filename
    raise ApiError(400, "missing_avatar", "Avatar file field is required.")


def safe_avatar_extension(filename: str, content: bytes) -> str:
    lower_name = filename.lower()
    if content.startswith(b"\xff\xd8\xff"):
        return ".jpg"
    if content.startswith(b"\x89PNG\r\n\x1a\n"):
        return ".png"
    if content.startswith(b"RIFF") and b"WEBP" in content[:16]:
        return ".webp"
    if lower_name.endswith((".jpg", ".jpeg")):
        return ".jpg"
    if lower_name.endswith(".png"):
        return ".png"
    if lower_name.endswith(".webp"):
        return ".webp"
    raise ApiError(400, "unsupported_avatar_type", "Avatar must be JPG, PNG, or WebP.")


class TouchHandler(BaseHTTPRequestHandler):
    server_version = "TouchLocalBackend/0.1"

    def log_message(self, fmt: str, *args: Any) -> None:
        # Avoid logging request bodies or tokens.
        print(f"{self.address_string()} - {fmt % args}")

    def do_OPTIONS(self) -> None:
        self.send_response(204)
        self.write_common_headers()
        self.end_headers()

    def do_GET(self) -> None:
        self.handle_request("GET")

    def do_POST(self) -> None:
        self.handle_request("POST")

    def do_PATCH(self) -> None:
        self.handle_request("PATCH")

    def do_DELETE(self) -> None:
        self.handle_request("DELETE")

    def handle_request(self, method: str) -> None:
        try:
            path = urlparse(self.path).path
            if method == "GET" and path == "/health":
                self.respond_json(200, {"status": "ok", "time": utc_now()})
                return
            if method == "GET" and path == "/account/me":
                self.handle_account_me()
                return
            if method == "GET" and path == "/meetings":
                self.handle_meetings()
                return
            if method == "GET" and path == "/friends":
                self.handle_friends()
                return
            if method == "GET" and path == "/friends/search":
                self.handle_friend_search()
                return
            if method == "GET" and path == "/day-events":
                self.handle_day_events()
                return
            if method == "GET" and path == "/events":
                self.handle_events()
                return
            if method == "GET" and path == "/events/stream":
                self.handle_events_stream()
                return
            if method == "GET" and path.startswith("/uploads/avatars/"):
                self.handle_avatar_file(path)
                return
            if method == "GET" and path.startswith("/uploads/card-backgrounds/"):
                self.handle_card_background_file(path)
                return
            if method == "POST" and path == "/auth/register":
                self.handle_register()
                return
            if method == "POST" and path == "/auth/login":
                self.handle_login()
                return
            if method == "POST" and path == "/auth/refresh":
                self.handle_refresh()
                return
            if method == "POST" and path == "/auth/logout":
                self.handle_logout()
                return
            if method == "PATCH" and path == "/account/me":
                self.handle_account_update()
                return
            if method == "POST" and path == "/account/me":
                self.handle_account_update()
                return
            if method == "POST" and path == "/account/avatar":
                self.handle_avatar_upload()
                return
            if method == "POST" and path == "/account/card-background":
                self.handle_card_background_upload()
                return
            if method == "POST" and path == "/devices/register":
                self.handle_device_register()
                return
            if method == "POST" and path == "/meet-tokens":
                self.handle_meet_token_create()
                return
            if method == "POST" and path == "/meetings/proofs":
                self.handle_meeting_proof_submit()
                return
            if method == "POST" and path == "/friends/requests":
                self.handle_friend_request_create()
                return
            if method == "POST" and path == "/friends/requests/respond":
                self.handle_friend_request_respond()
                return
            if method == "POST" and path == "/friends/remove":
                self.handle_friend_remove()
                return
            if method == "POST" and path == "/friends/block":
                self.handle_friend_block()
                return
            if method == "POST" and path == "/friends/remark":
                self.handle_friend_remark()
                return
            if method == "POST" and path == "/day-events":
                self.handle_day_event_create()
                return
            if method == "POST" and path.startswith("/day-events/"):
                self.handle_day_event_update(path)
                return
            if method == "DELETE" and path.startswith("/day-events/"):
                self.handle_day_event_delete(path)
                return
            raise ApiError(404, "not_found", "Endpoint not found.")
        except ApiError as exc:
            self.respond_json(exc.status, {"error": {"code": exc.code, "message": exc.message}})
        except Exception as exc:
            print(f"Unhandled error: {type(exc).__name__}: {exc}")
            self.respond_json(500, {"error": {"code": "internal_error", "message": "Internal server error."}})

    def read_json_body(self, max_bytes: int = MAX_BODY_BYTES) -> dict[str, Any]:
        content_length = int(self.headers.get("Content-Length", "0"))
        if content_length > max_bytes:
            raise ApiError(413, "body_too_large", "Request body is too large.")
        raw_body = self.rfile.read(content_length)
        try:
            parsed = json.loads(raw_body.decode("utf-8") if raw_body else "{}")
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            raise ApiError(400, "invalid_json", "Request body must be valid JSON.") from exc
        if not isinstance(parsed, dict):
            raise ApiError(400, "invalid_json", "Request body must be a JSON object.")
        return parsed

    def request_headers(self) -> dict[str, str]:
        return {key.lower(): value for key, value in self.headers.items()}

    def handle_register(self) -> None:
        body = self.read_json_body()
        display_name = str(body.get("displayName", "")).strip()
        email = normalize_email(str(body.get("email", "")))
        password = str(body.get("password", ""))
        if not display_name:
            raise ApiError(400, "missing_display_name", "Display name is required.")
        if len(display_name) > 40:
            raise ApiError(400, "display_name_too_long", "Display name is too long.")
        validate_password(password)

        user_id = f"user_{uuid.uuid4().hex}"
        salt, password_hash = hash_password(password)
        now = utc_now()

        try:
            with connect_db() as db:
                if display_name_is_taken(db, display_name):
                    raise ApiError(409, "display_name_taken", "这个昵称已被占用，请换一个昵称。")
                db.execute(
                    """
                    INSERT INTO users (
                        id, display_name, email, password_salt, password_hash, created_at,
                        bio, birthday, gender, card_background_path, card_background_key, last_seen_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, 'mizuki', ?)
                    """,
                    (user_id, display_name, email, salt, password_hash, now, now),
                )
                session = create_session(db, user_id)
                user_row = db.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
        except sqlite3.IntegrityError as exc:
            raise ApiError(409, "email_already_registered", "Email is already registered.") from exc

        self.respond_json(201, {"user": public_user(user_from_row(user_row)), **session})

    def handle_login(self) -> None:
        body = self.read_json_body()
        email = normalize_email(str(body.get("email", "")))
        password = str(body.get("password", ""))

        with connect_db() as db:
            row = db.execute("SELECT * FROM users WHERE email = ?", (email,)).fetchone()
            if row is None or not verify_password(password, row["password_salt"], row["password_hash"]):
                raise ApiError(401, "invalid_credentials", "Email or password is incorrect.")
            mark_user_seen(db, row["id"])
            session = create_session(db, row["id"])
            updated_row = db.execute("SELECT * FROM users WHERE id = ?", (row["id"],)).fetchone()

        self.respond_json(200, {"user": public_user(user_from_row(updated_row)), **session})

    def handle_refresh(self) -> None:
        body = self.read_json_body()
        refresh_token = str(body.get("refreshToken", ""))
        if not refresh_token:
            raise ApiError(400, "missing_refresh_token", "Refresh token is required.")

        with connect_db() as db:
            row = db.execute(
                """
                SELECT * FROM refresh_tokens
                WHERE token_hash = ? AND revoked_at IS NULL
                """,
                (hash_token(refresh_token),),
            ).fetchone()
            if row is None or row["expires_at"] < utc_now():
                raise ApiError(401, "invalid_refresh_token", "Refresh token is invalid or expired.")

            db.execute("UPDATE refresh_tokens SET revoked_at = ? WHERE id = ?", (utc_now(), row["id"]))
            mark_user_seen(db, row["user_id"])
            session = create_session(db, row["user_id"])
            user_row = db.execute("SELECT * FROM users WHERE id = ?", (row["user_id"],)).fetchone()
            if user_row is None:
                raise ApiError(401, "invalid_refresh_token", "Refresh token user no longer exists.")

        self.respond_json(200, {"user": public_user(user_from_row(user_row)), **session})

    def handle_logout(self) -> None:
        body = self.read_json_body()
        refresh_token = str(body.get("refreshToken", ""))
        if refresh_token:
            with connect_db() as db:
                db.execute(
                    "UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL",
                    (utc_now(), hash_token(refresh_token)),
                )
        self.respond_json(200, {"status": "logged_out"})

    def handle_account_me(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        with connect_db() as db:
            mark_user_seen(db, user_id)
            row = db.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
            if row is None:
                raise ApiError(401, "user_not_found", "Authenticated user no longer exists.")
        self.respond_json(200, {"user": public_user(user_from_row(row))})

    def handle_meetings(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        params = parse_qs(urlparse(self.path).query)
        friend_user_id = params.get("friendUserId", [""])[0].strip()
        with connect_db() as db:
            visible_ids = accepted_friend_ids(db, user_id)
            if friend_user_id:
                if friend_user_id not in visible_ids:
                    self.respond_json(200, {"meetings": []})
                    return
                rows = db.execute(
                    """
                    SELECT * FROM meetings
                    WHERE owner_user_id = ? AND person_user_id = ?
                    ORDER BY met_date DESC, met_time DESC
                    """,
                    (user_id, friend_user_id),
                ).fetchall()
            else:
                if not visible_ids:
                    self.respond_json(200, {"meetings": []})
                    return
                rows = db.execute(
                    """
                    SELECT * FROM meetings
                    WHERE owner_user_id = ?
                      AND person_user_id IN ({})
                    ORDER BY met_date DESC, met_time DESC
                    """.format(",".join("?" for _ in visible_ids)),
                    (user_id, *visible_ids),
                ).fetchall()
        self.respond_json(200, {"meetings": [public_meeting(row) for row in rows]})

    def handle_account_update(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        body = self.read_json_body()
        display_name = str(body.get("displayName", "")).strip()
        if not display_name:
            raise ApiError(400, "missing_display_name", "Display name is required.")
        if len(display_name) > 40:
            raise ApiError(400, "display_name_too_long", "Display name is too long.")
        bio = normalize_optional_text(body.get("bio"), 160)
        birthday = normalize_birthday(body.get("birthday"))
        gender = normalize_gender(body.get("gender"))
        card_background_key = str(body.get("cardBackgroundKey", "")).strip()
        if card_background_key and card_background_key not in DEFAULT_CARD_BACKGROUND_KEYS:
            raise ApiError(400, "invalid_card_background", "名片背景选项不合法。")

        with connect_db() as db:
            if display_name_is_taken(db, display_name, excluding_user_id=user_id):
                raise ApiError(409, "display_name_taken", "这个昵称已被占用，请换一个昵称。")
            if card_background_key:
                db.execute(
                    """
                    UPDATE users
                    SET display_name = ?, bio = ?, birthday = ?, gender = ?,
                        card_background_key = ?, card_background_path = NULL, last_seen_at = ?
                    WHERE id = ?
                    """,
                    (display_name, bio, birthday, gender, card_background_key, utc_now(), user_id),
                )
            else:
                db.execute(
                    """
                    UPDATE users
                    SET display_name = ?, bio = ?, birthday = ?, gender = ?, last_seen_at = ?
                    WHERE id = ?
                    """,
                    (display_name, bio, birthday, gender, utc_now(), user_id),
                )
            row = db.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
            if row is None:
                raise ApiError(401, "user_not_found", "Authenticated user no longer exists.")
        self.respond_json(200, {"user": public_user(user_from_row(row))})

    def handle_avatar_upload(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        content_type = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in content_type or "boundary=" not in content_type:
            raise ApiError(400, "invalid_multipart", "Avatar upload must use multipart/form-data.")

        content_length = int(self.headers.get("Content-Length", "0"))
        if content_length <= 0 or content_length > MAX_AVATAR_BYTES + 4096:
            raise ApiError(413, "avatar_too_large", "Avatar image is too large.")

        raw_body = self.rfile.read(content_length)
        boundary = content_type.split("boundary=", 1)[1].strip().strip('"')
        avatar_bytes, filename = parse_multipart_file(raw_body, boundary, "avatar")
        if len(avatar_bytes) > MAX_AVATAR_BYTES:
            raise ApiError(413, "avatar_too_large", "Avatar image is too large.")

        extension = safe_avatar_extension(filename, avatar_bytes)
        stored_name = f"{user_id}_{uuid.uuid4().hex}{extension}"
        UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
        (UPLOAD_DIR / stored_name).write_bytes(avatar_bytes)

        with connect_db() as db:
            old_row = db.execute("SELECT avatar_path FROM users WHERE id = ?", (user_id,)).fetchone()
            if old_row is None:
                raise ApiError(401, "user_not_found", "Authenticated user no longer exists.")
            old_avatar = old_row["avatar_path"]
            db.execute("UPDATE users SET avatar_path = ? WHERE id = ?", (stored_name, user_id))
            row = db.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()

        if old_avatar:
            old_path = UPLOAD_DIR / old_avatar
            if old_path.exists() and old_path.is_file():
                old_path.unlink()

        self.respond_json(200, {"user": public_user(user_from_row(row))})

    def handle_card_background_upload(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        content_type = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in content_type or "boundary=" not in content_type:
            raise ApiError(400, "invalid_multipart", "Background upload must use multipart/form-data.")

        content_length = int(self.headers.get("Content-Length", "0"))
        if content_length <= 0 or content_length > MAX_CARD_BACKGROUND_BYTES + 4096:
            raise ApiError(413, "background_too_large", "Card background image is too large.")

        raw_body = self.rfile.read(content_length)
        boundary = content_type.split("boundary=", 1)[1].strip().strip('"')
        image_bytes, filename = parse_multipart_file(raw_body, boundary, "background")
        if len(image_bytes) > MAX_CARD_BACKGROUND_BYTES:
            raise ApiError(413, "background_too_large", "Card background image is too large.")

        extension = safe_avatar_extension(filename, image_bytes)
        stored_name = f"{user_id}_{uuid.uuid4().hex}{extension}"
        CARD_BACKGROUND_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
        (CARD_BACKGROUND_UPLOAD_DIR / stored_name).write_bytes(image_bytes)

        with connect_db() as db:
            old_row = db.execute("SELECT card_background_path FROM users WHERE id = ?", (user_id,)).fetchone()
            if old_row is None:
                raise ApiError(401, "user_not_found", "Authenticated user no longer exists.")
            old_background = old_row["card_background_path"]
            db.execute(
                """
                UPDATE users
                SET card_background_path = ?, card_background_key = NULL, last_seen_at = ?
                WHERE id = ?
                """,
                (stored_name, utc_now(), user_id),
            )
            row = db.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()

        if old_background:
            old_path = CARD_BACKGROUND_UPLOAD_DIR / old_background
            if old_path.exists() and old_path.is_file():
                old_path.unlink()

        self.respond_json(200, {"user": public_user(user_from_row(row))})

    def handle_avatar_file(self, path: str) -> None:
        filename = path.rsplit("/", 1)[-1]
        if "/" in filename or "\\" in filename or ".." in filename:
            raise ApiError(400, "invalid_file", "Avatar path is invalid.")
        file_path = UPLOAD_DIR / filename
        if not file_path.exists() or not file_path.is_file():
            raise ApiError(404, "not_found", "Avatar not found.")

        raw = file_path.read_bytes()
        self.send_response(200)
        self.write_common_headers()
        self.send_header("Content-Type", mimetypes.guess_type(filename)[0] or "application/octet-stream")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def handle_card_background_file(self, path: str) -> None:
        filename = path.rsplit("/", 1)[-1]
        if "/" in filename or "\\" in filename or ".." in filename:
            raise ApiError(400, "invalid_file", "Background path is invalid.")
        file_path = CARD_BACKGROUND_UPLOAD_DIR / filename
        if not file_path.exists() or not file_path.is_file():
            raise ApiError(404, "not_found", "Background not found.")

        raw = file_path.read_bytes()
        self.send_response(200)
        self.write_common_headers()
        self.send_header("Content-Type", mimetypes.guess_type(filename)[0] or "application/octet-stream")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def handle_device_register(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        body = self.read_json_body()
        device_name = str(body.get("deviceName", "")).strip()
        public_key = str(body.get("publicKey", "")).strip()
        key_algorithm = str(body.get("keyAlgorithm", "")).strip()
        app_version = str(body.get("appVersion", "")).strip()

        if not device_name:
            raise ApiError(400, "missing_device_name", "Device name is required.")
        if not public_key:
            raise ApiError(400, "missing_public_key", "Public key is required.")
        if not key_algorithm:
            raise ApiError(400, "missing_key_algorithm", "Key algorithm is required.")
        if not app_version:
            raise ApiError(400, "missing_app_version", "App version is required.")

        device_id = f"dev_{uuid.uuid4().hex}"
        now = utc_now()
        with connect_db() as db:
            db.execute(
                """
                INSERT INTO devices
                (id, user_id, device_name, public_key, key_algorithm, app_version, created_at, revoked_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
                """,
                (device_id, user_id, device_name, public_key, key_algorithm, app_version, now),
            )

        self.respond_json(
            201,
            {
                "device": {
                    "id": device_id,
                    "deviceName": device_name,
                    "keyAlgorithm": key_algorithm,
                    "appVersion": app_version,
                    "createdAt": now,
                }
            },
        )

    def handle_friends(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        with connect_db() as db:
            rows = db.execute(
                """
                SELECT * FROM friendships
                WHERE requester_user_id = ? OR addressee_user_id = ?
                ORDER BY updated_at DESC
                """,
                (user_id, user_id),
            ).fetchall()
            payload = [public_friendship(db, row, user_id) for row in rows]
        self.respond_json(200, {"friendships": payload})

    def handle_events(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        params = parse_qs(urlparse(self.path).query)
        after_id = int(params.get("afterId", ["0"])[0] or "0")
        with connect_db() as db:
            rows = db.execute(
                """
                SELECT * FROM user_events
                WHERE user_id = ? AND id > ?
                ORDER BY id ASC
                LIMIT 100
                """,
                (user_id, after_id),
            ).fetchall()
        self.respond_json(200, {"events": [public_user_event(row) for row in rows]})

    def handle_events_stream(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        params = parse_qs(urlparse(self.path).query)
        last_id = int(params.get("afterId", ["0"])[0] or "0")

        self.send_response(200)
        self.write_common_headers()
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Connection", "keep-alive")
        self.end_headers()

        started_at = utc_now()
        try:
            while utc_now() - started_at < 300:
                with connect_db() as db:
                    rows = db.execute(
                        """
                        SELECT * FROM user_events
                        WHERE user_id = ? AND id > ?
                        ORDER BY id ASC
                        LIMIT 25
                        """,
                        (user_id, last_id),
                    ).fetchall()
                if rows:
                    for row in rows:
                        event = public_user_event(row)
                        last_id = int(event["id"])
                        raw = json.dumps(event, ensure_ascii=False)
                        self.wfile.write(f"id: {last_id}\n".encode("utf-8"))
                        self.wfile.write(f"event: {event['type']}\n".encode("utf-8"))
                        self.wfile.write(f"data: {raw}\n\n".encode("utf-8"))
                    self.wfile.flush()
                else:
                    self.wfile.write(b": keep-alive\n\n")
                    self.wfile.flush()
                time.sleep(2)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            return

    def handle_friend_search(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        params = parse_qs(urlparse(self.path).query)
        raw_q = params.get("q", [""])[0].strip()
        q = raw_q[:40]
        if not q:
            self.respond_json(200, {"users": []})
            return
        with connect_db() as db:
            rows = db.execute(
                """
                SELECT * FROM users
                WHERE id != ? AND lower(display_name) LIKE lower(?)
                ORDER BY display_name ASC
                LIMIT 20
                """,
                (user_id, f"%{q}%"),
            ).fetchall()
        self.respond_json(200, {"users": [public_user_row(row) for row in rows]})

    def handle_friend_request_create(self) -> None:
        requester_user_id = get_authenticated_user_id(self.request_headers())
        body = self.read_json_body()
        target_user_id = str(body.get("targetUserId", "")).strip()
        if not target_user_id:
            raise ApiError(400, "missing_target_user", "Target user is required.")
        if target_user_id == requester_user_id:
            raise ApiError(400, "self_friend_not_allowed", "You cannot add yourself as a friend.")

        now = utc_now()
        with connect_db() as db:
            target = db.execute("SELECT * FROM users WHERE id = ?", (target_user_id,)).fetchone()
            if target is None:
                raise ApiError(404, "user_not_found", "User was not found.")
            existing = get_friendship_between(db, requester_user_id, target_user_id)
            if existing is None:
                db.execute(
                    """
                    INSERT INTO friendships
                    (id, requester_user_id, addressee_user_id, status, requester_blocked_at, addressee_blocked_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'pending', NULL, NULL, ?, ?)
                    """,
                    (f"fr_{uuid.uuid4().hex}", requester_user_id, target_user_id, now, now),
                )
            elif existing["status"] == "removed":
                db.execute(
                    """
                    UPDATE friendships
                    SET requester_user_id = ?, addressee_user_id = ?, status = 'pending',
                        requester_blocked_at = NULL, addressee_blocked_at = NULL, updated_at = ?
                    WHERE id = ?
                    """,
                    (requester_user_id, target_user_id, now, existing["id"]),
                )
            else:
                payload = public_friendship(db, existing, requester_user_id)
                self.respond_json(200, {"friendship": payload})
                return
            row = get_friendship_between(db, requester_user_id, target_user_id)
            payload = public_friendship(db, row, requester_user_id)
            target_payload = public_friendship(db, row, target_user_id)
            requester = db.execute("SELECT * FROM users WHERE id = ?", (requester_user_id,)).fetchone()
            create_user_event(
                db,
                target_user_id,
                "friend_request_received",
                {
                    "friendship": target_payload,
                    "fromUser": public_user_row(requester) if requester else None,
                },
                now,
            )
        self.respond_json(201, {"friendship": payload})

    def handle_friend_request_respond(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        body = self.read_json_body()
        friendship_id = str(body.get("friendshipId", "")).strip()
        action = str(body.get("action", "")).strip()
        if action not in ("accept", "reject"):
            raise ApiError(400, "invalid_action", "Action must be accept or reject.")
        now = utc_now()
        with connect_db() as db:
            row = db.execute("SELECT * FROM friendships WHERE id = ?", (friendship_id,)).fetchone()
            if row is None:
                raise ApiError(404, "friendship_not_found", "Friend request was not found.")
            if row["addressee_user_id"] != user_id:
                raise ApiError(403, "not_request_receiver", "Only the request receiver can respond.")
            next_status = "accepted" if action == "accept" else "removed"
            db.execute("UPDATE friendships SET status = ?, updated_at = ? WHERE id = ?", (next_status, now, friendship_id))
            updated = db.execute("SELECT * FROM friendships WHERE id = ?", (friendship_id,)).fetchone()
            payload = public_friendship(db, updated, user_id)
            event_type = "friend_request_accepted" if action == "accept" else "friend_request_rejected"
            create_user_event(
                db,
                row["requester_user_id"],
                event_type,
                {
                    "friendship": public_friendship(db, updated, row["requester_user_id"]),
                    "fromUser": public_user_row(db.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()),
                },
                now,
            )
        self.respond_json(200, {"friendship": payload})

    def handle_friend_remove(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        body = self.read_json_body()
        friend_user_id = str(body.get("friendUserId", "")).strip()
        now = utc_now()
        with connect_db() as db:
            row = get_friendship_between(db, user_id, friend_user_id)
            if row is None:
                raise ApiError(404, "friendship_not_found", "Friendship was not found.")
            db.execute(
                """
                UPDATE friendships
                SET status = 'removed', requester_blocked_at = NULL, addressee_blocked_at = NULL, updated_at = ?
                WHERE id = ?
                """,
                (now, row["id"]),
            )
            other_user_id = row["addressee_user_id"] if row["requester_user_id"] == user_id else row["requester_user_id"]
            create_user_event(
                db,
                other_user_id,
                "friend_removed",
                {"friendUserId": user_id},
                now,
            )
        self.respond_json(200, {"status": "removed"})

    def handle_friend_block(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        body = self.read_json_body()
        friend_user_id = str(body.get("friendUserId", "")).strip()
        blocked = bool(body.get("blocked", False))
        now = utc_now()
        with connect_db() as db:
            row = get_friendship_between(db, user_id, friend_user_id)
            if row is None or row["status"] != "accepted":
                raise ApiError(404, "friendship_not_found", "Accepted friendship was not found.")
            column = "requester_blocked_at" if row["requester_user_id"] == user_id else "addressee_blocked_at"
            db.execute(
                f"UPDATE friendships SET {column} = ?, updated_at = ? WHERE id = ?",
                (now if blocked else None, now, row["id"]),
            )
            updated = db.execute("SELECT * FROM friendships WHERE id = ?", (row["id"],)).fetchone()
            payload = public_friendship(db, updated, user_id)
        self.respond_json(200, {"friendship": payload})

    def handle_friend_remark(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        body = self.read_json_body()
        friend_user_id = str(body.get("friendUserId", "")).strip()
        remark = str(body.get("remark", "")).strip()[:40]
        now = utc_now()
        with connect_db() as db:
            row = get_friendship_between(db, user_id, friend_user_id)
            if row is None or row["status"] != "accepted":
                raise ApiError(404, "friendship_not_found", "Accepted friendship was not found.")
            column = "requester_remark" if row["requester_user_id"] == user_id else "addressee_remark"
            db.execute(
                f"UPDATE friendships SET {column} = ?, updated_at = ? WHERE id = ?",
                (remark or None, now, row["id"]),
            )
            updated = db.execute("SELECT * FROM friendships WHERE id = ?", (row["id"],)).fetchone()
            payload = public_friendship(db, updated, user_id)
        self.respond_json(200, {"friendship": payload})

    def handle_day_events(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        params = parse_qs(urlparse(self.path).query)
        event_date = params.get("date", [""])[0].strip()
        if len(event_date) != 10:
            raise ApiError(400, "invalid_date", "Event date is required.")
        with connect_db() as db:
            rows = db.execute(
                """
                SELECT * FROM day_events
                WHERE owner_user_id = ? AND event_date = ?
                ORDER BY created_at DESC
                """,
                (user_id, event_date),
            ).fetchall()
            payload = [
                event for row in rows
                if (event := public_day_event_for_viewer(db, row, user_id)) is not None
            ]
        self.respond_json(200, {"events": payload})

    def handle_day_event_create(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        body = self.read_json_body(max_bytes=8 * 1024 * 1024)
        event_date = str(body.get("eventDate", "")).strip()
        title = str(body.get("title", "")).strip()[:60] or "\u4eca\u5929\u53d1\u751f\u7684\u4e8b"
        note = str(body.get("note", "")).strip()[:2000]
        participant_ids = body.get("participantUserIds", [])
        image_values = body.get("imageBase64List", [])
        if image_values in (None, ""):
            image_values = []
        if not isinstance(image_values, list):
            raise ApiError(400, "invalid_images", "Event images must be a list.")
        image_base64_list = [str(value).strip() for value in image_values if str(value).strip()]
        legacy_image = str(body.get("imageBase64", "")).strip()
        if legacy_image and not image_base64_list:
            image_base64_list = [legacy_image]
        if len(event_date) != 10:
            raise ApiError(400, "invalid_date", "Event date is required.")
        if not isinstance(participant_ids, list):
            raise ApiError(400, "invalid_participants", "Participants must be a list.")
        participant_ids = [str(value).strip() for value in participant_ids if str(value).strip()]
        if len(participant_ids) > 20:
            raise ApiError(400, "too_many_participants", "Too many participants.")
        if len(image_base64_list) > 6:
            raise ApiError(400, "too_many_images", "Too many event images.")
        image_payload = json.dumps(image_base64_list, separators=(",", ":")) if image_base64_list else None
        if image_payload is not None and len(image_payload) > 7_600_000:
            raise ApiError(413, "image_too_large", "Event image is too large.")

        now = utc_now()
        event_id = f"evt_{uuid.uuid4().hex}"
        with connect_db() as db:
            for friend_user_id in participant_ids:
                friendship = get_friendship_between(db, user_id, friend_user_id)
                if not friendship_is_active(friendship):
                    raise ApiError(403, "participant_not_active_friend", "Event participants must be active friends.")
            db.execute(
                """
                INSERT INTO day_events
                (id, owner_user_id, event_date, title, note, image_base64, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (event_id, user_id, event_date, title, note, image_payload, now, now),
            )
            for friend_user_id in participant_ids:
                db.execute(
                    """
                    INSERT INTO day_event_participants (event_id, friend_user_id)
                    VALUES (?, ?)
                    """,
                    (event_id, friend_user_id),
                )
            row = db.execute("SELECT * FROM day_events WHERE id = ?", (event_id,)).fetchone()
            payload = public_day_event(db, row)
        self.respond_json(201, {"event": payload})

    def handle_day_event_delete(self, path: str) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        event_id = unquote(path.removeprefix("/day-events/")).strip()
        if not event_id.startswith("evt_"):
            raise ApiError(400, "invalid_event_id", "Event id is invalid.")
        with connect_db() as db:
            row = db.execute(
                "SELECT * FROM day_events WHERE id = ? AND owner_user_id = ?",
                (event_id, user_id),
            ).fetchone()
            if row is None:
                raise ApiError(404, "event_not_found", "Event was not found.")
            db.execute("DELETE FROM day_events WHERE id = ? AND owner_user_id = ?", (event_id, user_id))
        self.respond_json(200, {"status": "deleted"})

    def handle_day_event_update(self, path: str) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        event_id = unquote(path.removeprefix("/day-events/")).strip()
        if not event_id.startswith("evt_"):
            raise ApiError(400, "invalid_event_id", "Event id is invalid.")
        body = self.read_json_body(max_bytes=8 * 1024 * 1024)
        title = str(body.get("title", "")).strip()[:60] or "\u4eca\u5929\u53d1\u751f\u7684\u4e8b"
        note = str(body.get("note", "")).strip()[:2000]
        participant_ids = body.get("participantUserIds", [])
        image_values = body.get("imageBase64List", [])
        if image_values in (None, ""):
            image_values = []
        if not isinstance(participant_ids, list):
            raise ApiError(400, "invalid_participants", "Participants must be a list.")
        if not isinstance(image_values, list):
            raise ApiError(400, "invalid_images", "Event images must be a list.")
        participant_ids = [str(value).strip() for value in participant_ids if str(value).strip()]
        image_base64_list = [str(value).strip() for value in image_values if str(value).strip()]
        if len(participant_ids) > 20:
            raise ApiError(400, "too_many_participants", "Too many participants.")
        if len(image_base64_list) > 6:
            raise ApiError(400, "too_many_images", "Too many event images.")
        image_payload = json.dumps(image_base64_list, separators=(",", ":")) if image_base64_list else None
        if image_payload is not None and len(image_payload) > 7_600_000:
            raise ApiError(413, "image_too_large", "Event image is too large.")
        now = utc_now()
        with connect_db() as db:
            row = db.execute(
                "SELECT * FROM day_events WHERE id = ? AND owner_user_id = ?",
                (event_id, user_id),
            ).fetchone()
            if row is None:
                raise ApiError(404, "event_not_found", "Event was not found.")
            for friend_user_id in participant_ids:
                friendship = get_friendship_between(db, user_id, friend_user_id)
                if not friendship_is_active(friendship):
                    raise ApiError(403, "participant_not_active_friend", "Event participants must be active friends.")
            db.execute(
                """
                UPDATE day_events
                SET title = ?, note = ?, image_base64 = ?, updated_at = ?
                WHERE id = ? AND owner_user_id = ?
                """,
                (title, note, image_payload, now, event_id, user_id),
            )
            db.execute("DELETE FROM day_event_participants WHERE event_id = ?", (event_id,))
            for friend_user_id in participant_ids:
                db.execute(
                    """
                    INSERT INTO day_event_participants (event_id, friend_user_id)
                    VALUES (?, ?)
                    """,
                    (event_id, friend_user_id),
                )
            updated = db.execute("SELECT * FROM day_events WHERE id = ?", (event_id,)).fetchone()
            payload = public_day_event_for_viewer(db, updated, user_id)
        self.respond_json(200, {"event": payload})

    def handle_meet_token_create(self) -> None:
        user_id = get_authenticated_user_id(self.request_headers())
        token = f"mt_{secrets.token_urlsafe(32)}"
        token_id = f"mtok_{uuid.uuid4().hex}"
        issued_at = utc_now()
        expires_at = issued_at + MEET_TOKEN_TTL_SECONDS

        with connect_db() as db:
            user_row = db.execute("SELECT id FROM users WHERE id = ?", (user_id,)).fetchone()
            if user_row is None:
                raise ApiError(401, "user_not_found", "Authenticated user no longer exists.")
            db.execute(
                """
                INSERT INTO meet_tokens (id, token_hash, owner_user_id, issued_at, expires_at, used_at)
                VALUES (?, ?, ?, ?, ?, NULL)
                """,
                (token_id, hash_token(token), user_id, issued_at, expires_at),
            )

        self.respond_json(
            201,
            {
                "nfcPayload": {
                    "version": 1,
                    "token": token,
                    "issuedAt": issued_at,
                    "expiresAt": expires_at,
                },
                "expiresAt": expires_at,
            },
        )

    def handle_meeting_proof_submit(self) -> None:
        scanner_user_id = get_authenticated_user_id(self.request_headers())
        body = self.read_json_body()
        client_submission_id = str(body.get("clientSubmissionId", "")).strip()
        payload = body.get("nfcPayload")
        if not client_submission_id:
            raise ApiError(400, "missing_submission_id", "Client submission id is required.")
        if not isinstance(payload, dict):
            raise ApiError(400, "invalid_nfc_payload", "NFC payload must be an object.")
        if payload.get("version") != 1:
            raise ApiError(400, "unsupported_payload_version", "NFC payload version is not supported.")
        token = str(payload.get("token", "")).strip()
        if not token.startswith("mt_") or len(token) > 128:
            raise ApiError(400, "invalid_meet_token", "Meeting token is invalid.")

        now = utc_now()
        with connect_db() as db:
            existing = db.execute(
                """
                SELECT meetings.*
                FROM meeting_proof_submissions
                JOIN meetings ON meetings.id = meeting_proof_submissions.scanner_meeting_id
                WHERE meeting_proof_submissions.scanner_user_id = ?
                  AND meeting_proof_submissions.client_submission_id = ?
                """,
                (scanner_user_id, client_submission_id),
            ).fetchone()
            if existing is not None:
                self.respond_json(200, {"status": "confirmed", "meeting": public_meeting(existing)})
                return

            token_row = db.execute(
                "SELECT * FROM meet_tokens WHERE token_hash = ?",
                (hash_token(token),),
            ).fetchone()
            if token_row is None:
                raise ApiError(404, "meet_token_not_found", "Meeting token was not found.")
            if token_row["used_at"] is not None:
                raise ApiError(409, "meet_token_used", "Meeting token has already been used.")
            if int(token_row["expires_at"]) < now:
                raise ApiError(409, "meet_token_expired", "Meeting token has expired.")
            scanned_user_id = token_row["owner_user_id"]
            if scanned_user_id == scanner_user_id:
                raise ApiError(400, "self_meeting_not_allowed", "You cannot create a meeting with yourself.")

            scanner = db.execute("SELECT * FROM users WHERE id = ?", (scanner_user_id,)).fetchone()
            scanned = db.execute("SELECT * FROM users WHERE id = ?", (scanned_user_id,)).fetchone()
            if scanner is None or scanned is None:
                raise ApiError(401, "user_not_found", "Meeting user no longer exists.")
            friendship = get_friendship_between(db, scanner_user_id, scanned_user_id)
            if not friendship_is_active(friendship):
                self.respond_json(
                    200,
                    {
                        "status": "friend_required",
                        "user": public_user_row(scanned),
                        "friendship": public_friendship(db, friendship, scanner_user_id) if friendship else None,
                    },
                )
                return

            met_date, met_time = format_met_date_time(now)
            scanner_meeting_id = f"meet_{uuid.uuid4().hex}"
            scanned_meeting_id = f"meet_{uuid.uuid4().hex}"
            db.execute("UPDATE meet_tokens SET used_at = ? WHERE id = ?", (now, token_row["id"]))
            db.execute(
                """
                INSERT INTO meetings
                (id, owner_user_id, person_user_id, person_name, met_date, met_time, status, method, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'confirmed', 'NFC_HCE', ?)
                """,
                (scanner_meeting_id, scanner_user_id, scanned_user_id, scanned["display_name"], met_date, met_time, now),
            )
            db.execute(
                """
                INSERT INTO meetings
                (id, owner_user_id, person_user_id, person_name, met_date, met_time, status, method, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'confirmed', 'NFC_HCE', ?)
                """,
                (scanned_meeting_id, scanned_user_id, scanner_user_id, scanner["display_name"], met_date, met_time, now),
            )
            db.execute(
                """
                INSERT INTO meeting_proof_submissions
                (id, scanner_user_id, client_submission_id, token_id, scanner_meeting_id, scanned_meeting_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    f"mproof_{uuid.uuid4().hex}",
                    scanner_user_id,
                    client_submission_id,
                    token_row["id"],
                    scanner_meeting_id,
                    scanned_meeting_id,
                    now,
                ),
            )
            meeting_row = db.execute("SELECT * FROM meetings WHERE id = ?", (scanner_meeting_id,)).fetchone()
            scanned_meeting_row = db.execute("SELECT * FROM meetings WHERE id = ?", (scanned_meeting_id,)).fetchone()
            create_user_event(
                db,
                scanner_user_id,
                "meeting_confirmed",
                {
                    "meeting": public_meeting(meeting_row),
                    "peer": public_user_row(scanned),
                    "role": "scanner",
                },
                now,
            )
            create_user_event(
                db,
                scanned_user_id,
                "meeting_confirmed",
                {
                    "meeting": public_meeting(scanned_meeting_row),
                    "peer": public_user_row(scanner),
                    "role": "scanned",
                },
                now,
            )

        self.respond_json(201, {"status": "confirmed", "meeting": public_meeting(meeting_row)})

    def respond_json(self, status: int, payload: dict[str, Any]) -> None:
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.write_common_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def write_common_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.send_header("Cache-Control", "no-store")


def main() -> None:
    init_db()
    server = ThreadingHTTPServer((HOST, PORT), TouchHandler)
    print(f"Touch local backend listening on http://{HOST}:{PORT}")
    print("Do not expose this development server directly to the internet.")
    server.serve_forever()


if __name__ == "__main__":
    main()
