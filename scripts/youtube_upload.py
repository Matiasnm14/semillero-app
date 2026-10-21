#!/usr/bin/env python3
"""Subida reanudable de YouTube para el piloto y el worker; secretos y recibos fuera de Git."""

import argparse
from contextlib import contextmanager
from datetime import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import time
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

from google.auth.exceptions import GoogleAuthError
from google.auth.transport.requests import AuthorizedSession, Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
import requests


ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / ".local/youtube"
API = "https://www.googleapis.com/youtube/v3"
UPLOAD = "https://www.googleapis.com/upload/youtube/v3/videos"
SCOPES = ["https://www.googleapis.com/auth/youtube.upload",
          "https://www.googleapis.com/auth/youtube.readonly"]
CHUNK_SIZE = 8 * 1024 * 1024


class UploadError(Exception):
    """Mensaje apto para consola: nunca contiene tokens ni códigos OAuth."""


def save_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd, temporary = tempfile.mkstemp(dir=path.parent, prefix=".youtube-")
    try:
        with os.fdopen(fd, "w") as stream:
            json.dump(value, stream, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def load_json(path):
    return json.loads(Path(path).read_text())


def authorize(client_file, token_file, port, force=False, interactive=True):
    config = load_json(client_file)
    if "installed" not in config:
        raise UploadError("Esta utilidad necesita un cliente OAuth de aplicación de escritorio")
    credentials = None
    if Path(token_file).exists() and not force:
        saved = load_json(token_file)
        if saved.get("client_id") == config["installed"].get("client_id"):
            credentials = Credentials.from_authorized_user_info(saved)
            if not credentials.has_scopes(SCOPES):
                credentials = None
    if credentials and not credentials.valid and credentials.refresh_token:
        credentials.refresh(Request())
    if credentials is None or not credentials.valid:
        if not interactive:
            raise UploadError("La autorización necesita intervención del propietario; ejecuta authorize --reauthorize")
        flow = InstalledAppFlow.from_client_secrets_file(
            str(client_file), SCOPES, autogenerate_code_verifier=True)
        credentials = flow.run_local_server(
            host="127.0.0.1", port=port, open_browser=False, timeout_seconds=300,
            prompt="consent select_account",
            authorization_prompt_message="Abre este enlace con la cuenta del canal:\n{url}\n",
            success_message="Solicitud recibida. Puedes volver a Semillero.")
        if not credentials.has_scopes(SCOPES):
            raise UploadError("No se concedieron los permisos de subida y consulta necesarios")
    save_json(token_file, json.loads(credentials.to_json()))
    return credentials


def ensure_ok(response, action):
    if 200 <= response.status_code < 300:
        return response.json()
    reason = ""
    try:
        errors = response.json().get("error", {}).get("errors", [])
        reason = errors[0].get("reason", "") if errors else ""
    except (ValueError, AttributeError, IndexError):
        pass
    reason = re.sub(r"[^a-zA-Z0-9_-]", "", reason)[:80]
    raise UploadError(f"{action}: HTTP {response.status_code}" + (f" ({reason})" if reason else ""))


def verify_channel(session, handle):
    expected = ensure_ok(session.get(
        API + "/channels", params={"part": "snippet", "forHandle": handle.lstrip("@")},
        timeout=30), "Consultar identificador del canal").get("items", [])
    mine = ensure_ok(session.get(
        API + "/channels", params={"part": "snippet", "mine": "true"},
        timeout=30), "Consultar canal autorizado").get("items", [])
    if len(expected) != 1:
        raise UploadError("No se encontró el canal esperado; revisa su @identificador")
    if len(mine) != 1 or mine[0]["id"] != expected[0]["id"]:
        raise UploadError("La cuenta autorizada no corresponde al canal esperado. "
                          "Ejecuta authorize --reauthorize y selecciona el canal correcto")
    channel = {"id": mine[0]["id"], "handle": "@" + handle.lstrip("@"),
               "title": mine[0]["snippet"]["title"]}
    print(f"Canal verificado: {channel['handle']} ({channel['id']})", flush=True)
    return channel


def fingerprint(file):
    digest = hashlib.sha256()
    with Path(file).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def valid_session_uri(uri):
    parsed = urlparse(uri)
    return (parsed.scheme == "https" and not parsed.username and not parsed.password
            and parsed.hostname is not None
            and (parsed.hostname == "googleapis.com" or parsed.hostname.endswith(".googleapis.com")))


@contextmanager
def exclusive_upload(record):
    record.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with record.with_suffix(".lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise UploadError("Ya hay una subida usando este registro local") from None
        yield


def upload_file(session, file, title, channel_id, record, category_id="22"):
    file, record = Path(file), Path(record)
    size = file.stat().st_size
    if size <= 0:
        raise UploadError("El video está vacío")
    identity = {"sha256": fingerprint(file), "sizeBytes": size,
                "title": title, "channelId": channel_id, "privacyStatus": "unlisted"}
    state = load_json(record) if record.exists() else dict(identity)
    if any(state.get(key) != value for key, value in identity.items()):
        raise UploadError("El registro corresponde a otro archivo, título o canal. "
                          "Usa otro --record para una prueba nueva")
    if state.get("videoId"):
        print("El video ya fue subido; se consultará el existente.", flush=True)
        return state
    if not state.get("sessionUri"):
        response = session.post(
            UPLOAD, params={"uploadType": "resumable", "part": "snippet,status",
                            "notifySubscribers": "false"},
            json={"snippet": {"title": title, "categoryId": category_id},
                  "status": {"privacyStatus": "unlisted"}},
            headers={"X-Upload-Content-Length": str(size), "X-Upload-Content-Type": "video/mp4"},
            timeout=30)
        if response.status_code != 200:
            ensure_ok(response, "Iniciar subida")
        uri = response.headers.get("Location", "")
        if not valid_session_uri(uri):
            raise UploadError("YouTube no devolvió una sesión HTTPS válida")
        state.update(sessionUri=uri, confirmedBytes=0, state="UPLOADING", sourceFile=file.name)
        save_json(record, state)
    uri = state["sessionUri"]
    if not valid_session_uri(uri):
        raise UploadError("La sesión guardada no pertenece a Google")

    # Consultar siempre antes de enviar: un proceso anterior pudo perder la respuesta final.
    needs_probe = True
    failures = 0
    with file.open("rb") as stream:
        while True:
            offset = state.get("confirmedBytes", 0)
            headers = {"Content-Range": f"bytes */{size}", "Content-Length": "0"}
            data = b""
            if not needs_probe:
                stream.seek(offset)
                data = stream.read(CHUNK_SIZE)
                if not data:
                    raise UploadError("YouTube aún no confirmó la finalización; vuelve a ejecutar upload")
                headers = {"Content-Type": "video/mp4", "Content-Length": str(len(data)),
                           "Content-Range": f"bytes {offset}-{offset + len(data) - 1}/{size}"}
            try:
                response = session.put(uri, data=data, headers=headers,
                                       allow_redirects=False, timeout=(20, 60))
                if response.status_code in (429, 500, 502, 503, 504):
                    failures += 1
                    if failures > 5:
                        raise UploadError("YouTube sigue sin responder; la sesión queda guardada para reanudar")
                    needs_probe = True
                    time.sleep(min(2 ** failures, 16))
                    continue
            except requests.RequestException:
                failures += 1
                if failures > 5:
                    raise UploadError("Se interrumpió la conexión; vuelve a ejecutar upload para consultar/reanudar") from None
                needs_probe = True
                time.sleep(min(2 ** failures, 16))
                continue
            if response.status_code in (200, 201):
                video_id = response.json().get("id")
                if not isinstance(video_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id):
                    raise UploadError("Respuesta final sin ID válido; conserva la sesión y consulta de nuevo")
                state.update(videoId=video_id, confirmedBytes=size, state="UPLOADED")
                save_json(record, state)
                return state
            if response.status_code in (404, 410):
                state["state"] = "REVIEW_REQUIRED"
                save_json(record, state)
                raise UploadError("La sesión expiró. Revisa el canal antes de crear otra subida para evitar duplicados")
            if response.status_code != 308:
                ensure_ok(response, "Transferir video")
                raise UploadError("Respuesta inesperada al transferir el video")
            confirmed = response.headers.get("Range", "")
            match = re.fullmatch(r"bytes=0-(\d+)", confirmed)
            if confirmed and not match:
                raise UploadError("YouTube devolvió un rango inválido; se conserva la sesión")
            next_offset = int(match.group(1)) + 1 if match else 0
            if not 0 <= next_offset <= size or (not needs_probe and next_offset <= offset):
                raise UploadError("YouTube no confirmó un progreso válido; se conserva la sesión")
            state["confirmedBytes"] = next_offset
            save_json(record, state)
            print(f"Subida: {100 * next_offset / size:.0f}%", flush=True)
            if next_offset > offset:
                failures = 0
            needs_probe = False


def verify_video(session, record, wait_seconds=60):
    record = Path(record)
    state = load_json(record)
    if not state.get("videoId"):
        raise UploadError("Todavía no hay ID remoto; ejecuta upload para continuar la sesión guardada")
    deadline = time.monotonic() + wait_seconds
    while True:
        items = ensure_ok(session.get(
            API + "/videos", params={"part": "snippet,status,processingDetails", "id": state["videoId"]},
            timeout=30), "Comprobar video").get("items", [])
        if not items:
            raise UploadError("YouTube aún no devolvió el video; conserva el registro y ejecuta status")
        video = items[0]
        status, snippet = video["status"], video["snippet"]
        state.update(actualTitle=snippet["title"], actualPrivacy=status["privacyStatus"],
                     actualChannelId=snippet["channelId"], uploadStatus=status.get("uploadStatus"),
                     processingStatus=video.get("processingDetails", {}).get("processingStatus"),
                     url="https://www.youtube.com/watch?v=" + state["videoId"])
        matches = (state["actualTitle"] == state["title"] and state["actualPrivacy"] == "unlisted"
                   and state["actualChannelId"] == state["channelId"])
        if not matches:
            state["state"] = "METADATA_MISMATCH"
            save_json(record, state)
            raise UploadError("El video existe, pero su título, canal o visibilidad no coincide. "
                              f"Visibilidad real: {state['actualPrivacy']}; ID: {state['videoId']}")
        failed = (state["uploadStatus"] in ("failed", "rejected", "deleted")
                  or state["processingStatus"] in ("failed", "terminated"))
        ready = state["uploadStatus"] == "processed" or state["processingStatus"] == "succeeded"
        state["state"] = "FAILED" if failed else "AVAILABLE" if ready else "PROCESSING"
        save_json(record, state)
        if failed:
            raise UploadError("YouTube rechazó el video o falló su procesamiento; revisa YouTube Studio")
        if ready or time.monotonic() >= deadline:
            print(json.dumps({key: state.get(key) for key in (
                "videoId", "actualTitle", "actualPrivacy", "state", "url")}, indent=2), flush=True)
            return state
        time.sleep(min(5, max(0, deadline - time.monotonic())))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("authorize", "upload", "status"))
    parser.add_argument("--channel", default="Semillero-Video-Analysis")
    parser.add_argument("--file", type=Path, default=ROOT / "cronoshift.mp4")
    parser.add_argument("--title", default=datetime.now(ZoneInfo("America/La_Paz")).date().isoformat())
    parser.add_argument("--client", type=Path, default=LOCAL / "client_secret.json")
    parser.add_argument("--tokens", type=Path, default=LOCAL / "tokens.json")
    parser.add_argument("--record", type=Path, default=LOCAL / "cronoshift-upload-state.json")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--reauthorize", action="store_true")
    parser.add_argument("--non-interactive", action="store_true",
                        help="No iniciar consentimiento OAuth desde un worker del servidor")
    parser.add_argument("--wait-seconds", type=int, default=60)
    parser.add_argument("--category", choices=("17", "22"), default="22",
                        help="17: deportes; 22: categoría del piloto")
    args = parser.parse_args()
    if args.wait_seconds < 0:
        parser.error("--wait-seconds no puede ser negativo")
    try:
        if args.command == "upload" and not args.file.is_file():
            raise UploadError("No se encontró el archivo de video")
        credentials = authorize(args.client, args.tokens, args.port, args.reauthorize,
                                interactive=not args.non_interactive)
        with AuthorizedSession(credentials) as session:
            channel = verify_channel(session, args.channel)
            save_json(LOCAL / "channel.json", channel)
            if args.command == "authorize":
                print("Autorización guardada. Ya puedes ejecutar upload.", flush=True)
            elif args.command == "upload":
                with exclusive_upload(args.record):
                    upload_file(session, args.file, args.title, channel["id"], args.record, args.category)
                    verify_video(session, args.record, wait_seconds=args.wait_seconds)
            else:
                if load_json(args.record).get("channelId") != channel["id"]:
                    raise UploadError("El registro no corresponde al canal autorizado")
                verify_video(session, args.record, wait_seconds=args.wait_seconds)
        save_json(args.tokens, json.loads(credentials.to_json()))
        return 0
    except UploadError as error:
        print(f"ERROR: {error}", flush=True)
    except GoogleAuthError:
        print("ERROR: Google no pudo renovar la autorización; ejecuta authorize --reauthorize.", flush=True)
    except requests.RequestException:
        print("ERROR: Falló la conexión con Google. Los registros locales se conservan.", flush=True)
    except (OSError, ValueError):
        print("ERROR: No se pudo leer la configuración, el archivo o el registro local.", flush=True)
    except Exception as error:
        # No imprimir excepciones de OAuth: pueden contener la URL de retorno y códigos.
        print(f"ERROR: No se completó la operación ({type(error).__name__}). "
              "Comprueba la pantalla de Google y que la cuenta sea usuario de prueba.", flush=True)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
