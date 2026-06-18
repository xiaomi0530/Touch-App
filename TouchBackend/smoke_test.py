from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
import uuid


BASE_URL = "http://127.0.0.1:8000"


def request(method: str, path: str, payload: dict | None = None, token: str | None = None) -> dict:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{BASE_URL}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8")
        raise RuntimeError(f"{method} {path} failed with {exc.code}: {body}") from exc


def upload_avatar(token: str) -> dict:
    boundary = f"SmokeBoundary{uuid.uuid4().hex}"
    png_1x1 = (
        b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01"
        b"\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89"
        b"\x00\x00\x00\rIDATx\x9cc\xf8\xff\xff?\x00\x05\xfe"
        b"\x02\xfeA\xe2(\xb3\x00\x00\x00\x00IEND\xaeB`\x82"
    )
    body = (
        f"--{boundary}\r\n"
        'Content-Disposition: form-data; name="avatar"; filename="avatar.png"\r\n'
        "Content-Type: image/png\r\n\r\n"
    ).encode("utf-8") + png_1x1 + f"\r\n--{boundary}--\r\n".encode("utf-8")

    req = urllib.request.Request(
        f"{BASE_URL}/account/avatar",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8")
        raise RuntimeError(f"POST /account/avatar failed with {exc.code}: {body}") from exc


def main() -> None:
    suffix = int(time.time())
    email = f"dev{suffix}@example.com"
    password = "password123"

    health = request("GET", "/health")
    print("health:", health["status"])

    registered = request(
        "POST",
        "/auth/register",
        {
            "displayName": "Local Dev",
            "email": email,
            "password": password,
        },
    )
    print("registered:", registered["user"]["email"])

    me = request("GET", "/account/me", token=registered["accessToken"])
    print("me:", me["user"]["displayName"])

    updated = request("POST", "/account/me", {"displayName": "Local Dev Updated"}, token=registered["accessToken"])
    print("profile:", updated["user"]["displayName"])

    avatar = upload_avatar(registered["accessToken"])
    print("avatar:", avatar["user"]["avatarUrl"])

    device = request(
        "POST",
        "/devices/register",
        {
            "deviceName": "Android Emulator",
            "publicKey": "dev-public-key-placeholder",
            "keyAlgorithm": "EC_P256_SHA256",
            "appVersion": "1.0",
        },
        token=registered["accessToken"],
    )
    print("device:", device["device"]["id"])

    login = request("POST", "/auth/login", {"email": email, "password": password})
    print("login:", login["user"]["id"])

    refreshed = request("POST", "/auth/refresh", {"refreshToken": login["refreshToken"]})
    print("refresh:", refreshed["user"]["email"])

    logout = request("POST", "/auth/logout", {"refreshToken": refreshed["refreshToken"]})
    print("logout:", logout["status"])

    scanner = request(
        "POST",
        "/auth/register",
        {
            "displayName": "Scanner User",
            "email": f"scanner{suffix}@example.com",
            "password": password,
        },
    )
    scanned = request(
        "POST",
        "/auth/register",
        {
            "displayName": "Scanned User",
            "email": f"scanned{suffix}@example.com",
            "password": password,
        },
    )
    meet_token = request("POST", "/meet-tokens", token=scanned["accessToken"])
    submission_id = f"smoke-{uuid.uuid4().hex}"
    blocked_proof = request(
        "POST",
        "/meetings/proofs",
        {
            "clientSubmissionId": submission_id,
            "nfcPayload": meet_token["nfcPayload"],
        },
        token=scanner["accessToken"],
    )
    print("meet_requires_friend:", blocked_proof["status"])
    friend_request = request(
        "POST",
        "/friends/requests",
        {"targetUserId": scanned["user"]["id"]},
        token=scanner["accessToken"],
    )
    accepted = request(
        "POST",
        "/friends/requests/respond",
        {"friendshipId": friend_request["friendship"]["id"], "action": "accept"},
        token=scanned["accessToken"],
    )
    print("friendship:", accepted["friendship"]["status"])
    meet_token = request("POST", "/meet-tokens", token=scanned["accessToken"])
    submission_id = f"smoke-{uuid.uuid4().hex}"
    proof = request(
        "POST",
        "/meetings/proofs",
        {
            "clientSubmissionId": submission_id,
            "nfcPayload": meet_token["nfcPayload"],
        },
        token=scanner["accessToken"],
    )
    repeated = request(
        "POST",
        "/meetings/proofs",
        {
            "clientSubmissionId": submission_id,
            "nfcPayload": meet_token["nfcPayload"],
        },
        token=scanner["accessToken"],
    )
    scanner_meetings = request("GET", "/meetings", token=scanner["accessToken"])
    scanned_meetings = request("GET", "/meetings", token=scanned["accessToken"])
    print("meet_proof:", proof["status"], proof["meeting"]["personName"])
    print("meet_idempotent:", repeated["meeting"]["id"] == proof["meeting"]["id"])
    print("meet_records:", len(scanner_meetings["meetings"]), len(scanned_meetings["meetings"]))
    remark = request(
        "POST",
        "/friends/remark",
        {"friendUserId": scanned["user"]["id"], "remark": "Smoke Friend"},
        token=scanner["accessToken"],
    )
    print("friend_remark:", remark["friendship"]["remark"])
    event = request(
        "POST",
        "/day-events",
        {
            "eventDate": proof["meeting"]["metDate"],
            "title": "Smoke Event",
            "note": "Created by smoke test.",
            "participantUserIds": [scanned["user"]["id"]],
            "imageBase64List": ["c21va2UtaW1hZ2UtMQ==", "c21va2UtaW1hZ2UtMg=="],
        },
        token=scanner["accessToken"],
    )
    events = request("GET", f"/day-events?date={proof['meeting']['metDate']}", token=scanner["accessToken"])
    print("day_event:", event["event"]["title"], len(events["events"]), len(event["event"]["imageBase64List"]))
    delete_event = request("DELETE", f"/day-events/{event['event']['id']}", token=scanner["accessToken"])
    events_after_delete = request("GET", f"/day-events?date={proof['meeting']['metDate']}", token=scanner["accessToken"])
    print("day_event_delete:", delete_event["status"], len(events_after_delete["events"]))

    seeded = request("POST", "/auth/login", {"email": "test@163.com", "password": "12345678"})
    seeded_meetings = request("GET", "/meetings", token=seeded["accessToken"])
    print("seeded_user:", seeded["user"]["displayName"])
    print("seeded_meetings:", len(seeded_meetings["meetings"]))


if __name__ == "__main__":
    main()
