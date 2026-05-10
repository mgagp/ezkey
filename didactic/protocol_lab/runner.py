"""Execute Admin + Auth + Crypto parity flow and persist artifacts."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Optional

from .clients import AdminClient, AuthClient, CryptoClient
from .config import ScenarioConfig


def _utc_now_iso() -> str:
  return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class StepLedger:
  """Append-only steps log + periodic state snapshots."""

  def __init__(self, artifacts_dir: Path) -> None:
    self._dir = artifacts_dir
    self._dir.mkdir(parents=True, exist_ok=True)
    self._steps_path = self._dir / "steps.jsonl"
    self._steps_path.unlink(missing_ok=True)
    self.state: dict[str, Any] = {"version": 1, "updatedAt": _utc_now_iso()}

  def emit(self, row: dict[str, Any]) -> None:
    line = json.dumps(row, ensure_ascii=False) + "\n"
    with self._steps_path.open("a", encoding="utf-8") as f:
      f.write(line)

  def save_state(self) -> None:
    self.state["updatedAt"] = _utc_now_iso()
    path = self._dir / "state.json"
    path.write_text(
      json.dumps(self.state, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

  def save_summary(self, summary: dict[str, Any]) -> None:
    summary["generatedAt"] = _utc_now_iso()
    path = self._dir / "summary.json"
    path.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def _http_step(
  step_id: str,
  actor: str,
  result: Optional[Any],
) -> dict[str, Any]:
  if result is None:
    return {"id": step_id, "actor": actor, "http": None, "crypto": []}
  return {
    "id": step_id,
    "actor": actor,
    "http": result.redacted_dict(),
    "crypto": [],
  }


def _crypto_http_step(step_id: str, hr: Any) -> dict[str, Any]:
  return {"id": step_id, "actor": "crypto", "http": hr.redacted_dict(), "crypto": []}


def _require_ok(status: int, ctx: str, *, allowed: Optional[list[int]] = None) -> None:
  if allowed is None:
    allowed = [200]
  if status not in allowed:
    raise RuntimeError(f"{ctx}: HTTP {status} not in {allowed}")


def run_scenario(
  cfg: ScenarioConfig,
  artifacts_dir: Path,
  *,
  dry_run: bool = False,
  skip_admin_wait: bool = False,
) -> dict[str, Any]:
  ledger = StepLedger(artifacts_dir)
  summary: dict[str, Any] = {
    "dry_run": dry_run,
    "auth_attempt_ttl": {},
    "notes": [],
  }

  if dry_run:
    plan = [
      "admin.enrollment_create",
      "admin.enrollment_get",
      "mobile.enrollment_bind",
      "crypto.enrollment_bind_payload",
      "crypto.enrollment_bind_verify",
      "crypto.device_keypair",
      "crypto.enrollment_verify_device_payload",
      "crypto.enrollment_verify_sign",
      "mobile.enrollment_verify",
      "crypto.enrollment_verify_result_payload",
      "crypto.enrollment_verify_result_verify",
      "admin.auth_attempt_create",
      "crypto.device_proof_token",
      "crypto.device_proof_token_sign",
      "mobile.auth_pending",
      "crypto.pending_payload",
      "crypto.pending_verify",
      "crypto.respond_device_sign",
      "mobile.auth_respond",
      "crypto.respond_result_payload",
      "crypto.respond_result_verify",
    ]
    if not skip_admin_wait:
      plan.append("admin.auth_attempt_wait")
    ledger.state["planned_steps"] = plan
    ledger.save_state()
    ledger.save_summary(summary)
    return summary

  cfg.admin_token  # noqa: B018 — validate eagerly
  admin_client = AdminClient(cfg.admin_base, cfg.admin_token)
  auth_client = AuthClient(cfg.auth_base)
  crypto = CryptoClient(cfg.crypto_base)

  ec_body = cfg.enrollment_create_body()
  er = admin_client.request("POST", "/api/v1/enrollments", json_body=ec_body)
  ledger.emit(_http_step("admin.enrollment_create", "admin", er))
  _require_ok(er.status_code, "admin.enrollment_create", allowed=[201])
  assert isinstance(er.response_body, dict)
  enrollment_id = int(er.response_body["enrollmentId"])
  enrollment_challenge = int(er.response_body["enrollmentChallenge"])
  ledger.state["enrollment_id"] = enrollment_id
  ledger.state["enrollment_challenge"] = enrollment_challenge
  if er.response_body.get("expiresAt"):
    ledger.state["enrollment_expires_at"] = er.response_body.get("expiresAt")

  ge = admin_client.request("GET", f"/api/v1/enrollments/{enrollment_id}")
  ledger.emit(_http_step("admin.enrollment_get", "admin", ge))
  _require_ok(ge.status_code, "admin.enrollment_get")
  assert isinstance(ge.response_body, dict)
  enrollment_proof_token = ge.response_body["enrollmentProofToken"]
  ledger.state["enrollment_proof_token"] = enrollment_proof_token

  bind_req = {
    "enrollmentId": enrollment_id,
    "enrollmentProofToken": enrollment_proof_token,
  }
  bind = auth_client.request("POST", "/api/v1/enrollments/bind", json_body=bind_req)
  ledger.emit(_http_step("mobile.enrollment_bind", "mobile_enrollment", bind))
  _require_ok(bind.status_code, "mobile.enrollment_bind", allowed=[200])
  assert isinstance(bind.response_body, dict)
  integration_pub = bind.response_body["integrationPublicKey"]
  integration_alg = bind.response_body["integrationKeyAlgorithm"]
  bind_sig = bind.response_body["enrollmentBindPayloadSignedByIntegration"]
  bind_tid = bind.response_body.get("tenantId")

  ledger.state["integration_public_key"] = integration_pub

  bh = {
    "type": "enrollment-bind",
    "proofToken": bind.response_body["enrollmentProofToken"],
    "enrollmentId": enrollment_id,
    "integrationPublicKey": integration_pub,
    "integrationKeyAlgorithm": integration_alg,
    "integrationName": bind.response_body.get("integrationName"),
    "integrationDescription": bind.response_body.get("integrationDescription"),
    "enrollmentName": bind.response_body.get("enrollmentName"),
    "tenantId": bind_tid,
    "tenantName": bind.response_body.get("tenantName"),
    "tenantDescription": bind.response_body.get("tenantDescription"),
  }
  jb, jb_hr = crypto.post_json("/api/v1/crypto/payload-helper", bh)
  ledger.emit(_crypto_http_step("crypto.enrollment_bind_payload", jb_hr))
  _require_ok(jb_hr.status_code, "crypto.enrollment_bind_payload")
  bind_payload = jb["payload"]

  vv_body = {"data": bind_payload, "signature": bind_sig, "publicKey": integration_pub}
  vv, vv_hr = crypto.post_json("/api/v1/crypto/verify-ed25519", vv_body)
  ledger.emit(_crypto_http_step("crypto.enrollment_bind_verify", vv_hr))
  _require_ok(vv_hr.status_code, "crypto.enrollment_bind_verify")
  if not vv.get("valid"):
    raise RuntimeError("crypto.enrollment_bind_verify: Ed25519 verify failed")

  kp, kp_hr = crypto.get_json("/api/v1/crypto/keypair")
  ledger.emit(_crypto_http_step("crypto.device_keypair", kp_hr))
  _require_ok(kp_hr.status_code, "crypto.device_keypair", allowed=[200])
  device_private = kp["privateKey"]
  device_public = kp["publicKey"]
  ledger.state["device_public_key"] = device_public

  vd_body = {
    "type": "enrollment-verify-device",
    "proofToken": enrollment_proof_token,
    "enrollmentId": enrollment_id,
    "challengeResponse": enrollment_challenge,
    "devicePublicKey": device_public,
  }
  vd, vd_hr = crypto.post_json("/api/v1/crypto/payload-helper", vd_body)
  ledger.emit(_crypto_http_step("crypto.enrollment_verify_device_payload", vd_hr))
  _require_ok(vd_hr.status_code, "crypto.enrollment_verify_device_payload")
  canon_verify_device = vd["payload"]

  sign_body = {"data": canon_verify_device, "privateKey": device_private}
  sg, sg_hr = crypto.post_json("/api/v1/crypto/sign", sign_body)
  ledger.emit(_crypto_http_step("crypto.enrollment_verify_sign", sg_hr))
  _require_ok(sg_hr.status_code, "crypto.enrollment_verify_sign")
  proof_token_signed = sg["signature"]

  verify_req = {
    "enrollmentId": enrollment_id,
    "challengeResponse": enrollment_challenge,
    "devicePublicKey": device_public,
    "enrollmentProofTokenSigned": proof_token_signed,
    "devicePrivateKeyStorageTier": cfg.device_storage_tier,
  }
  vr = auth_client.request("POST", "/api/v1/enrollments/verify", json_body=verify_req)
  ledger.emit(_http_step("mobile.enrollment_verify", "mobile_enrollment", vr))
  _require_ok(vr.status_code, "mobile.enrollment_verify", allowed=[200])
  assert isinstance(vr.response_body, dict)
  if not vr.response_body.get("active"):
    summary["notes"].append("mobile.enrollment_verify: active=false (unexpected)")
  verify_msg = vr.response_body.get("enrollmentVerifyMessage") or ""
  verify_int_sig = vr.response_body["enrollmentVerifyPayloadSignedByIntegration"]

  eres_body = {
    "type": "enrollment-verify-result",
    "proofToken": enrollment_proof_token,
    "enrollmentId": enrollment_id,
    "result": "VERIFIED",
    "message": verify_msg,
  }
  rp, rp_hr = crypto.post_json("/api/v1/crypto/payload-helper", eres_body)
  ledger.emit(_crypto_http_step("crypto.enrollment_verify_result_payload", rp_hr))
  _require_ok(rp_hr.status_code, "crypto.enrollment_verify_result_payload")
  eres_payload = rp["payload"]

  vr2_body = {
    "data": eres_payload,
    "signature": verify_int_sig,
    "publicKey": integration_pub,
  }
  vr2, vr2_hr = crypto.post_json("/api/v1/crypto/verify-ed25519", vr2_body)
  ledger.emit(_crypto_http_step("crypto.enrollment_verify_result_verify", vr2_hr))
  _require_ok(vr2_hr.status_code, "crypto.enrollment_verify_result_verify")
  if not vr2.get("valid"):
    raise RuntimeError("crypto.enrollment_verify_result_verify failed")

  aa_body = {
    "enrollmentId": enrollment_id,
    "challengeRequested": cfg.auth_challenge_requested,
  }
  aa = admin_client.request("POST", "/api/v1/auth-attempts", json_body=aa_body)
  ledger.emit(_http_step("admin.auth_attempt_create", "admin", aa))
  _require_ok(aa.status_code, "admin.auth_attempt_create", allowed=[201])
  assert isinstance(aa.response_body, dict)
  auth_attempt_id = int(aa.response_body["authAttemptId"])
  summary["auth_attempt_ttl"]["timeoutSeconds"] = aa.response_body.get(
    "timeoutSeconds"
  )
  summary["auth_attempt_ttl"]["expiresAt"] = aa.response_body.get("expiresAt")
  summary["notes"].append(
    "Complete mobile respond before expiry (see summary.auth_attempt_ttl)."
  )
  ledger.save_state()

  pt, pt_hr = crypto.get_json("/api/v1/crypto/prooftoken")
  ledger.emit(_crypto_http_step("crypto.device_proof_token", pt_hr))
  _require_ok(pt_hr.status_code, "crypto.device_proof_token")
  device_session_proof = pt["proofToken"]

  sign_pt_body = {"data": device_session_proof, "privateKey": device_private}
  ps, ps_hr = crypto.post_json("/api/v1/crypto/sign", sign_pt_body)
  ledger.emit(_crypto_http_step("crypto.device_proof_token_sign", ps_hr))
  _require_ok(ps_hr.status_code, "crypto.device_proof_token_sign")
  device_proof_sig = ps["signature"]

  pending_body = {
    "enrollmentId": enrollment_id,
    "enrollmentProofToken": enrollment_proof_token,
    "deviceProofToken": device_session_proof,
    "deviceProofTokenSigned": device_proof_sig,
  }
  pr = auth_client.request("POST", "/api/v1/auth-attempts/pending", json_body=pending_body)
  ledger.emit(_http_step("mobile.auth_pending", "mobile_auth", pr))
  if pr.status_code == 204:
    raise RuntimeError(
      "mobile.auth_pending returned 204 — no pending attempt (ordering or race)."
    )
  _require_ok(pr.status_code, "mobile.auth_pending", allowed=[200])
  assert isinstance(pr.response_body, dict)
  auth_proof = pr.response_body["authAttemptProofToken"]
  pending_signed = pr.response_body["authAttemptProofTokenSignedByIntegration"]
  ch_req = bool(pr.response_body["authAttemptChallengeRequired"])
  ctx_title = pr.response_body.get("contextTitle") or ""
  ctx_msg = pr.response_body.get("contextMessage") or ""

  ledger.state["auth_attempt_id"] = auth_attempt_id
  ledger.state["auth_attempt_proof_token"] = auth_proof

  pending_ph = {
    "type": "pending",
    "proofToken": auth_proof,
    "challengeRequired": ch_req,
    "contextTitle": ctx_title,
    "contextMessage": ctx_msg,
  }
  pp, pp_hr = crypto.post_json("/api/v1/crypto/payload-helper", pending_ph)
  ledger.emit(_crypto_http_step("crypto.pending_payload", pp_hr))
  _require_ok(pp_hr.status_code, "crypto.pending_payload")

  pv_body = {
    "data": pp["payload"],
    "signature": pending_signed,
    "publicKey": integration_pub,
  }
  pv, pv_hr = crypto.post_json("/api/v1/crypto/verify-ed25519", pv_body)
  ledger.emit(_crypto_http_step("crypto.pending_verify", pv_hr))
  _require_ok(pv_hr.status_code, "crypto.pending_verify")
  if not pv.get("valid"):
    raise RuntimeError("crypto.pending_verify failed")

  accepted = cfg.respond_accepted
  respond_canon = f"{auth_proof}|{'true' if accepted else 'false'}"
  rs_body = {"data": respond_canon, "privateKey": device_private}
  rs, rs_hr = crypto.post_json("/api/v1/crypto/sign", rs_body)
  ledger.emit(_crypto_http_step("crypto.respond_device_sign", rs_hr))
  _require_ok(rs_hr.status_code, "crypto.respond_device_sign")
  respond_dev_sig = rs["signature"]

  challenge_response = 0
  if cfg.auth_challenge_requested:
    ach = aa.response_body.get("authAttemptChallenge")
    if ach is not None:
      challenge_response = int(ach)

  respond_req = {
    "authAttemptId": auth_attempt_id,
    "authAttemptAccepted": accepted,
    "authAttemptProofTokenSignedByDevice": respond_dev_sig,
    "authAttemptChallengeResponse": challenge_response,
  }
  rr = auth_client.request("POST", "/api/v1/auth-attempts/respond", json_body=respond_req)
  ledger.emit(_http_step("mobile.auth_respond", "mobile_auth", rr))
  _require_ok(rr.status_code, "mobile.auth_respond", allowed=[200])
  assert isinstance(rr.response_body, dict)
  result_enum = rr.response_body["authAttemptResult"]
  result_msg = rr.response_body.get("authAttemptMessage") or ""
  respond_result_sig = rr.response_body.get(
    "authAttemptProofTokenResultSignedByIntegration"
  )

  rr_pb = {
    "type": "respond-result",
    "proofToken": auth_proof,
    "authAttemptId": auth_attempt_id,
    "result": result_enum,
    "message": result_msg,
  }
  rr1, rr1_hr = crypto.post_json("/api/v1/crypto/payload-helper", rr_pb)
  ledger.emit(_crypto_http_step("crypto.respond_result_payload", rr1_hr))
  _require_ok(rr1_hr.status_code, "crypto.respond_result_payload")
  respond_result_payload = rr1["payload"]

  summary["respond_result_enum"] = result_enum

  if respond_result_sig:
    rv_body = {
      "data": respond_result_payload,
      "signature": respond_result_sig,
      "publicKey": integration_pub,
    }
    rv, rv_hr = crypto.post_json("/api/v1/crypto/verify-ed25519", rv_body)
    ledger.emit(_crypto_http_step("crypto.respond_result_verify", rv_hr))
    _require_ok(rv_hr.status_code, "crypto.respond_result_verify")
    if not rv.get("valid"):
      raise RuntimeError("crypto.respond_result_verify failed")
    summary["respond_result_signature_valid"] = True
  else:
    ledger.emit({
      "id": "crypto.respond_result_verify",
      "actor": "crypto",
      "http": None,
      "crypto": [],
      "note": "Skipped: missing respond-result signature field",
    })
    summary["respond_result_signature_valid"] = False
    summary["notes"].append(
      "Respond response omitted integration signature — verify step skipped."
    )

  if not skip_admin_wait:
    wq = {
      "timeout": cfg.wait_timeout,
      "polling": cfg.wait_poll,
    }
    wr = admin_client.request(
      "GET",
      f"/api/v1/auth-attempts/{auth_attempt_id}/wait",
      params=wq,
    )
    ledger.emit(_http_step("admin.auth_attempt_wait", "admin", wr))
    summary["admin_wait_http"] = wr.status_code
    summary["wait_response"] = wr.response_body
    # 408 is normal when timed out waiting; still artifact-worthy
    if wr.status_code not in (200, 408):
      raise RuntimeError(f"admin.auth_attempt_wait unexpected HTTP {wr.status_code}")

  ledger.save_state()
  ledger.save_summary(summary)
  return summary


def steps_index(lines: Iterable[dict[str, Any]]) -> dict[str, dict[str, Any]]:
  return {r["id"]: r for r in lines}


def load_steps_jsonl(path: Path) -> dict[str, dict[str, Any]]:
  out: dict[str, dict[str, Any]] = {}
  if not path.exists():
    return out
  with path.open(encoding="utf-8") as f:
    for raw in f:
      raw = raw.strip()
      if not raw:
        continue
      row = json.loads(raw)
      out[row["id"]] = row
  return out
