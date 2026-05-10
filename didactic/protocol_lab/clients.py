"""HTTP helpers for Admin and Auth APIs."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Optional

import requests


@dataclass
class HttpResult:
  method: str
  url: str
  request_headers: dict[str, str]
  request_body: Any
  status_code: int
  response_headers: dict[str, str]
  response_body: Any

  def redacted_dict(self) -> dict[str, Any]:
    return {
      "method": self.method,
      "url": self.url,
      "request_headers": self.request_headers,
      "request_body": self.request_body,
      "status_code": self.status_code,
      "response_headers": dict(self.response_headers),
      "response_body": self.response_body,
    }


def _parse_body(resp: requests.Response) -> Any:
  if not resp.content:
    return None
  ctype = resp.headers.get("Content-Type", "")
  if "application/json" in ctype:
    try:
      return resp.json()
    except Exception:
      return resp.text
  return resp.text


class AdminClient:
  def __init__(
    self,
    base_url: str,
    bearer_token: str,
    *,
    timeout: float = 120.0,
    verify: bool | str = True,
  ) -> None:
    self._base = base_url.rstrip("/")
    self._session = requests.Session()
    self._session.headers.update(
      {
        "Authorization": f"Bearer {bearer_token}",
        "Accept": "application/json",
      }
    )
    self._timeout = timeout
    self._verify = verify

  def request(
    self,
    method: str,
    path: str,
    *,
    json_body: Any = None,
    params: Optional[dict[str, Any]] = None,
  ) -> HttpResult:
    url = f"{self._base}{path}"
    headers = dict(self._session.headers)
    if json_body is not None:
      headers["Content-Type"] = "application/json"
    prep = requests.Request(
      method, url, headers=headers, json=json_body, params=params
    ).prepare()
    resp = self._session.send(prep, timeout=self._timeout, verify=self._verify)
    rb = _parse_body(resp)
    return HttpResult(
      method=method,
      url=url,
      request_headers=dict(prep.headers),
      request_body=json_body,
      status_code=resp.status_code,
      response_headers=dict(resp.headers),
      response_body=rb,
    )


class AuthClient:
  def __init__(
    self, base_url: str, *, timeout: float = 120.0, verify: bool | str = True
  ) -> None:
    self._base = base_url.rstrip("/")
    self._session = requests.Session()
    self._session.headers.update({"Accept": "application/json"})
    self._timeout = timeout
    self._verify = verify

  def request(
    self,
    method: str,
    path: str,
    *,
    json_body: Any = None,
  ) -> HttpResult:
    url = f"{self._base}{path}"
    headers: dict[str, str] = {"Accept": "application/json"}
    if json_body is not None:
      headers["Content-Type"] = "application/json"
    prep = requests.Request(method, url, headers=headers, json=json_body).prepare()
    resp = self._session.send(prep, timeout=self._timeout, verify=self._verify)
    rb = _parse_body(resp)
    return HttpResult(
      method=method,
      url=url,
      request_headers=dict(prep.headers),
      request_body=json_body,
      status_code=resp.status_code,
      response_headers=dict(resp.headers),
      response_body=rb,
    )


class CryptoClient:
  def __init__(self, base_url: str, *, timeout: float = 60.0, verify: bool | str = True):
    self._base = base_url.rstrip("/")
    self._session = requests.Session()
    self._timeout = timeout
    self._verify = verify

  def get_json(self, path: str) -> tuple[dict[str, Any], HttpResult]:
    url = f"{self._base}{path}"
    resp = self._session.get(
      url, timeout=self._timeout, verify=self._verify, headers={"Accept": "application/json"}
    )
    body = resp.json()
    hr = HttpResult(
      method="GET",
      url=url,
      request_headers=dict(resp.request.headers),
      request_body=None,
      status_code=resp.status_code,
      response_headers=dict(resp.headers),
      response_body=body,
    )
    return body, hr

  def post_json(self, path: str, body: dict[str, Any]) -> tuple[dict[str, Any], HttpResult]:
    url = f"{self._base}{path}"
    resp = self._session.post(
      url,
      json=body,
      timeout=self._timeout,
      verify=self._verify,
      headers={"Accept": "application/json", "Content-Type": "application/json"},
    )
    try:
      rbody = resp.json()
    except Exception:
      rbody = {"_raw_text": resp.text}
    hr = HttpResult(
      method="POST",
      url=url,
      request_headers=dict(resp.request.headers),
      request_body=body,
      status_code=resp.status_code,
      response_headers=dict(resp.headers),
      response_body=rbody,
    )
    return rbody, hr
