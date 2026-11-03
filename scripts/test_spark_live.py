#!/usr/bin/env python3
"""Run the explicit live Spark test without passing credentials in command-line arguments."""
import argparse
import json
from datetime import datetime, timezone
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--video", required=True, type=Path, help="Video autorizado, hasta 2 GiB")
    parser.add_argument("--config", type=Path, default=ROOT / ".local/analysis/.env")
    parser.add_argument("--db-url", required=True, help="JDBC de PostgreSQL local, base spark_live_test")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--with-youtube", action="store_true", help="Activar ambos workers y una publicación real no listada")
    args = parser.parse_args()
    video = args.video.resolve()
    if not video.is_file() or not 0 < video.stat().st_size <= 2 * 1024 * 1024 * 1024:
        parser.error("El video debe existir y medir entre 1 byte y 2 GiB")
    import re
    if not re.fullmatch(r"jdbc:postgresql://127\.0\.0\.1:[0-9]+/spark_live_test", args.db_url):
        parser.error("Solo se admite una base local spark_live_test")
    config = {}
    for line in args.config.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.removeprefix("export ").partition("=")
        if separator:
            value = value.strip()
            if len(value) >= 2 and value[0] in "\"'" and value[-1] == value[0]:
                value = value[1:-1]
            config[key.strip()] = value
    token = config.get("ANALYSIS_TOKEN", "")
    if not token or any(char.isspace() for char in token):
        parser.error("ANALYSIS_TOKEN falta o tiene espacios/saltos de línea")
    url = config.get("ANALYSIS_BASE_URL", "https://spark-0aa4.tail763771.ts.net").rstrip("/")
    if url != "https://spark-0aa4.tail763771.ts.net":
        parser.error("Esta prueba envía la credencial únicamente al endpoint Spark acordado")
    output = (args.output or ROOT / ".local/analysis" / ("live-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"))).resolve()
    if not output.is_relative_to((ROOT / ".local/analysis").resolve()):
        parser.error("Guardar la evidencia dentro de .local/analysis, excluido de Git")
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    if (output / "report.json").exists():
        parser.error("Ya hay un informe en ese destino; usa otra carpeta para conservarlo")
    env = dict(os.environ, ANALYSIS_TOKEN=token, ANALYSIS_BASE_URL=url, SPARK_LIVE_TEST="true",
               SPARK_LIVE_DB_URL=args.db_url, SPARK_LIVE_VIDEO=str(video), SPARK_LIVE_OUTPUT=str(output))
    env["YOUTUBE_LIVE_TEST"] = "true" if args.with_youtube else "false"
    if args.with_youtube:
        for key, relative in (("YOUTUBE_LIVE_PYTHON", "venv/bin/python"), ("YOUTUBE_LIVE_CLIENT", "client_secret.json"), ("YOUTUBE_LIVE_TOKENS", "tokens.json")):
            path = ROOT / ".local/youtube" / relative
            if not path.is_file():
                parser.error(f"Falta {path.relative_to(ROOT)} para probar YouTube")
            env[key] = str(path)
        env["YOUTUBE_LIVE_SCRIPT"] = str(ROOT / "scripts/youtube_upload.py")
    command = ["bash", "./mvnw", "--batch-mode", "--no-transfer-progress", "-pl", "semillero-api", "-am",
               "-Dtest=SparkLiveIT", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    log = output / "maven.log"
    with log.open("w") as stream:
        os.chmod(log, 0o600)
        print(f"Prueba real iniciada; evidencia en {output.relative_to(ROOT)}", flush=True)
        with subprocess.Popen(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT) as process:
            while True:
                try:
                    returncode = process.wait(timeout=15)
                    break
                except subprocess.TimeoutExpired:
                    try:
                        progress = json.loads((output / "progress.json").read_text())
                        print(f"Análisis {progress['analysisId']}: {progress['estado']}; avance {progress.get('porcentaje')}%", flush=True)
                    except (OSError, ValueError, KeyError):
                        print("La prueba sigue en ejecución; preparando Spring o recibiendo el video.", flush=True)
    # Keep private logs useful even if a dependency unexpectedly includes a credential in an error.
    log.write_text(log.read_text().replace(token, "[TOKEN_REDACTADO]"))
    print("Prueba aprobada" if returncode == 0 else "Prueba fallida; revisar report.json y maven.log", flush=True)
    return returncode


if __name__ == "__main__":
    sys.exit(main())
