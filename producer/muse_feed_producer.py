#!/usr/bin/env python3
"""muse_feed_producer.py - publish the live Muse Ambient feed.

Builds the Muse feed JSON (schema: ../MUSE-FEED.md) from verified live
sources on this VM and publishes it to the Hetzner VPS, where Tailscale
Serve exposes it on the tailnet-only :8443 service at an unguessable
token path. The Shield (a tailnet node) polls that URL every 5 minutes.

Sources (each verified live per run; a field is included ONLY when its
source answered this run - nothing is carried over from a previous run
except the brief cache, which is date-gated):
  houseMode : SmartThings location mode, live REST via the `st` wrapper
              (~/workspace/skills/smartthings/bin/st mode-current).
              Fallback: HA loopback sensor.house_mode.
  whoHome   : Google Location Sharing (gls CLI). Jonathan and Stephany
              count as home when a fix younger than 30 min lands within
              250 m of the home anchor (the same geofence family the
              house presence system uses).
  daisy     : bin/daisy_home.py verdict (SmartThings Find collar;
              fresh fix decides, stale collar = assumed home, and the
              note says which branch produced it).
  nextEvent : Google Calendar via hatch_gws_cli (+agenda, JSON),
              cache-first through producer/calendar-cache.json (the
              brief-sync cron mirrors the agenda every 15 min because
              gws times out under the systemd unit's environment).
              Earliest timed event starting in the future within 3 days;
              untitled events skipped; cross-calendar duplicates of the
              same event (same start, same location, title containment)
              collapsed to the cleaner title.
  brief     : producer/brief-lines.json, written by the
              muse-ambient-brief-sync runtime cron from the Daily Brief
              artifact's getbrief action. Used only when its briefDate
              is today (America/New_York) and it is < 6 h old.
  nowPlaying: HA loopback media_player.shield, only when its state is
              playing/paused, it carries a media_title, and the entity
              changed within the last 20 min. Otherwise omitted (the
              dashboard hides the zone by design).
  alerts    : HA HomeWhiz warning sensors that are actively on
              (dryer water tank full, dryer check filter, washer no
              water). Empty array when none fire.
  message   : intentionally unset. There is no real Muse note pipeline
              yet; a stale or invented note is worse than none.

Publish: local out/home.json is written first, then shipped over the
existing SSH lane to the VPS and atomically renamed into
/home/muse/muse-feed/home.json. Any failure before the rename leaves
the last good published file untouched (fail-silent). If NO source
verifies, the run exits 1 and publishes nothing.

State: producer/producer-state.json (last run summary, no secrets).
The feed token lives in producer/.feed-token (mode 600, gitignored,
never printed). The full URL is assembled at runtime only.
"""
import json
import math
import os
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

HERE = os.path.dirname(os.path.abspath(__file__))
HOME_DIR = os.path.expanduser("~")
SMART_HOME = os.path.join(HOME_DIR, "workspace/goals/manage-my-smart-home")
ST_BIN = os.path.join(HOME_DIR, "workspace/skills/smartthings/bin/st")
ST_LOCATION = "2663b018-14c3-4999-9f3b-4532b8f49dd0"
GLS_BIN = os.path.join(HOME_DIR, "workspace/custom-connectors/"
                       "custom.google-location-sharing/bin/gls")
DAISY_HOME = os.path.join(SMART_HOME, "bin/daisy_home.py")
GWS_BIN = "/opt/hatch/bin/hatch_gws_cli"
HA_BASE = "http://127.0.0.1:8123"
HA_OWNER = os.path.join(HOME_DIR, "homeassistant/config/muse_owner.json")
BRIEF_CACHE = os.path.join(HERE, "brief-lines.json")
OUT_FILE = os.path.join(HERE, "out/home.json")
STATE_FILE = os.path.join(HERE, "producer-state.json")
TOKEN_FILE = os.path.join(HERE, ".feed-token")
VPS = "muse@167.233.41.12"
VPS_KEY = os.path.join(HOME_DIR, ".ssh/id_ed25519")
VPS_FEED_DIR = "/home/muse/muse-feed"
HOME_ANCHOR = (40.754341, -73.918172)  # same anchor as stfind_ha_feed.py
GEOFENCE_M = 250
FIX_MAX_AGE_S = 1800
ET = ZoneInfo("America/New_York")


def run(cmd, timeout=60):
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    return p.returncode, p.stdout or "", p.stderr or ""


def haversine_m(a, b):
    (lat1, lon1), (lat2, lon2) = a, b
    r = 6371000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def src_house_mode(report):
    try:
        rc, out, _ = run([ST_BIN, "mode-current", ST_LOCATION], timeout=45)
        if rc == 0:
            label = json.loads(out).get("label")
            if label:
                report["houseMode"] = "smartthings-live"
                return label
    except Exception as e:
        report["houseMode_err"] = str(e)[:120]
    try:
        import urllib.request
        with open(HA_OWNER) as f:
            token = json.load(f)["token"]
        req = urllib.request.Request(
            f"{HA_BASE}/api/states/sensor.house_mode",
            headers={"Authorization": f"Bearer {token}"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            state = json.load(resp).get("state")
        if state and state not in ("unknown", "unavailable"):
            report["houseMode"] = "ha-sensor-fallback"
            return state.capitalize()
    except Exception as e:
        report["houseMode_err"] = str(e)[:120]
    return None


def src_who_home(report):
    try:
        rc, out, _ = run([GLS_BIN, "people"], timeout=60)
        if rc != 0:
            return None
        people = json.loads(out).get("people", [])
    except Exception as e:
        report["whoHome_err"] = str(e)[:120]
        return None
    home = []
    for want, label in (("Jonathan Cruzate", "Jonathan"),
                        ("Stephany Garces", "Stephany")):
        rec = next((p for p in people if p.get("full_name") == want), None)
        if not rec:
            continue
        age = rec.get("age_seconds")
        lat, lon = rec.get("latitude"), rec.get("longitude")
        if age is None or age > FIX_MAX_AGE_S or lat is None or lon is None:
            continue
        if haversine_m(HOME_ANCHOR, (lat, lon)) <= GEOFENCE_M:
            home.append(label)
    report["whoHome"] = "gls-live"
    return home


def src_daisy(report):
    try:
        rc, out, _ = run(["python3", DAISY_HOME], timeout=60)
        if rc != 0:
            return None
        d = json.loads(out)
    except Exception as e:
        report["daisy_err"] = str(e)[:120]
        return None
    verdict = d.get("verdict")
    if verdict not in ("home", "away"):
        return None
    age = d.get("collar_age_min")
    signal = d.get("signal") or ""
    if verdict == "home" and age is not None and age <= 120:
        note = f"Collar fix at home, {int(round(age))} min ago"
    elif verdict == "home":
        note = "Collar quiet; assumed home"
    else:
        note = "Collar away from home"
    report["daisy"] = f"daisy_home.py ({signal})"
    return {"status": "Home" if verdict == "home" else "Away", "note": note}


def _fmt_time(dt):
    return dt.strftime("%-I:%M %p")


CAL_CACHE = os.path.join(HERE, "calendar-cache.json")


def _load_events(report):
    """Calendar events, cache-first.

    hatch_gws_cli works from agent shells but times out under the
    systemd unit's environment (diagnosed 2026-11-21: every systemd run
    hit the 90 s cap while identical shell runs took ~7 s). The
    muse-ambient-brief-sync cron therefore mirrors the agenda into
    calendar-cache.json every 15 min; the producer prefers a cache
    younger than 20 min and falls back to a direct gws call (bounded
    at 45 s) only when the cache is missing or stale.
    """
    try:
        with open(CAL_CACHE) as f:
            cache = json.load(f)
        gen = datetime.fromisoformat(
            cache["generatedAt"].replace("Z", "+00:00"))
        age = (datetime.now(timezone.utc) - gen).total_seconds()
        if age <= 20 * 60:
            report["nextEvent_src"] = f"calendar-cache ({int(age)}s old)"
            return cache.get("events", [])
        report["nextEvent_src"] = "cache stale; direct gws"
    except FileNotFoundError:
        report["nextEvent_src"] = "no cache; direct gws"
    except Exception as e:
        report["nextEvent_src"] = f"cache unreadable ({e}); direct gws"
    rc, out, _ = run([GWS_BIN, "calendar", "+agenda", "--days", "3",
                      "--format", "json"], timeout=45)
    if rc != 0:
        raise RuntimeError(f"gws rc={rc}")
    return json.loads(out).get("events", [])


def src_next_event(report, now_et):
    try:
        events = _load_events(report)
    except Exception as e:
        report["nextEvent_err"] = str(e)[:120]
        return None
    cands = []
    for ev in events:
        title = (ev.get("summary") or "").strip()
        if not title or title == "(No title)":
            continue
        start_raw = ev.get("start") or ""
        try:
            start = datetime.fromisoformat(start_raw.replace("Z", "+00:00"))
        except ValueError:
            continue  # all-day events carry a bare date; skip for NEXT slot
        if start.tzinfo is None:
            continue
        start_et = start.astimezone(ET)
        if start_et <= now_et:
            continue
        cands.append({"title": title, "start": start_et,
                      "location": (ev.get("location") or "").strip()})
    if not cands:
        report["nextEvent"] = "calendar-live (none in 3 days)"
        return None
    cands.sort(key=lambda c: c["start"])
    # Collapse cross-calendar duplicates: same start + location, and one
    # title containing the other (e.g. "... | Partiful" mirrors).
    best = cands[0]
    for other in cands[1:]:
        if other["start"] == best["start"] and other["location"] == best["location"]:
            if best["title"].startswith(other["title"]):
                best = other
            elif other["title"].startswith(best["title"]):
                pass
            elif len(other["title"]) < len(best["title"]):
                best = other
    day = best["start"].date()
    if day == now_et.date():
        when = _fmt_time(best["start"])
    elif day == (now_et + timedelta(days=1)).date():
        when = f"Tomorrow, {_fmt_time(best['start'])}"
    else:
        when = f"{best['start'].strftime('%a')}, {_fmt_time(best['start'])}"
    report["nextEvent"] = "calendar-live"
    return {"title": best["title"], "time": when}


def src_brief(report, now_et):
    try:
        with open(BRIEF_CACHE) as f:
            cache = json.load(f)
        if cache.get("briefDate") != now_et.strftime("%Y-%m-%d"):
            report["brief"] = "cache wrong date; omitted"
            return None
        gen = datetime.fromisoformat(cache["generatedAt"].replace("Z", "+00:00"))
        if (datetime.now(timezone.utc) - gen).total_seconds() > 6 * 3600:
            report["brief"] = "cache >6h old; omitted"
            return None
        lines = [str(x)[:110] for x in cache.get("lines", [])][:4]
        if not lines:
            return None
        report["brief"] = "brief-cache (getbrief)"
        return {"title": cache.get("title") or "Daily Brief", "lines": lines}
    except FileNotFoundError:
        report["brief"] = "no cache yet; omitted"
        return None
    except Exception as e:
        report["brief_err"] = str(e)[:120]
        return None


def ha_states(report, entity_ids):
    import urllib.request
    with open(HA_OWNER) as f:
        token = json.load(f)["token"]
    out = {}
    for eid in entity_ids:
        req = urllib.request.Request(
            f"{HA_BASE}/api/states/{eid}",
            headers={"Authorization": f"Bearer {token}"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            out[eid] = json.load(resp)
    return out


def src_now_playing_and_alerts(report, now_utc):
    now_playing = None
    alerts = []
    try:
        states = ha_states(report, ["media_player.shield",
                                    "binary_sensor.dryer_water_tank_full",
                                    "binary_sensor.dryer_check_the_filter",
                                    "binary_sensor.washer_no_water"])
    except Exception as e:
        report["ha_err"] = str(e)[:120]
        return None, alerts
    mp = states.get("media_player.shield") or {}
    attrs = mp.get("attributes") or {}
    title = (attrs.get("media_title") or "").strip()
    if mp.get("state") in ("playing", "paused") and title:
        try:
            changed = datetime.fromisoformat(
                mp["last_changed"].replace("Z", "+00:00"))
            fresh = (now_utc - changed).total_seconds() <= 20 * 60
        except Exception:
            fresh = False
        if fresh:
            artist = (attrs.get("media_artist") or "").strip()
            now_playing = f"{title} - {artist}" if artist else title
            report["nowPlaying"] = "ha media_player.shield (fresh)"
    warn = {"binary_sensor.dryer_water_tank_full": "Dryer: water tank full",
            "binary_sensor.dryer_check_the_filter": "Dryer: check the filter",
            "binary_sensor.washer_no_water": "Washer: no water warning"}
    for eid, text in warn.items():
        if (states.get(eid) or {}).get("state") == "on":
            alerts.append(text)
    report["alerts"] = "ha homewhiz warning sensors"
    return now_playing, alerts


def publish(payload):
    os.makedirs(os.path.dirname(OUT_FILE), exist_ok=True)
    tmp = OUT_FILE + ".tmp"
    with open(tmp, "w") as f:
        json.dump(payload, f, indent=1, sort_keys=True)
    os.replace(tmp, OUT_FILE)
    remote_tmp = f"{VPS_FEED_DIR}/home.json.tmp"
    rc, _, err = run(["scp", "-i", VPS_KEY, "-o", "BatchMode=yes",
                      "-o", "ConnectTimeout=15", OUT_FILE,
                      f"{VPS}:{remote_tmp}"], timeout=60)
    if rc != 0:
        raise RuntimeError(f"scp failed: {err.strip()[:200]}")
    rc, _, err = run(["ssh", "-i", VPS_KEY, "-o", "BatchMode=yes",
                      "-o", "ConnectTimeout=15", VPS,
                      f"mv {remote_tmp} {VPS_FEED_DIR}/home.json"], timeout=45)
    if rc != 0:
        raise RuntimeError(f"remote mv failed: {err.strip()[:200]}")


def feed_url():
    with open(TOKEN_FILE) as f:
        token = f.read().strip()
    return f"https://vps.taila56db5.ts.net:8443/mf-{token}/home.json"


def main():
    now_utc = datetime.now(timezone.utc)
    now_et = now_utc.astimezone(ET)
    report = {"run_utc": now_utc.isoformat()}
    payload = {}
    verified = 0

    mode = src_house_mode(report)
    if mode:
        payload["houseMode"] = mode
        verified += 1
    who = src_who_home(report)
    if who is not None:
        payload["whoHome"] = who
        verified += 1
    daisy = src_daisy(report)
    if daisy:
        payload["daisy"] = daisy
        verified += 1
    nxt = src_next_event(report, now_et)
    if nxt:
        payload["nextEvent"] = nxt
        verified += 1
    brief = src_brief(report, now_et)
    if brief:
        payload["brief"] = brief
        verified += 1
    now_playing, alerts = src_now_playing_and_alerts(report, now_utc)
    if now_playing:
        payload["nowPlaying"] = now_playing
        verified += 1
    payload["alerts"] = alerts
    payload["updatedAt"] = now_utc.strftime("%Y-%m-%dT%H:%M:%SZ")

    if verified == 0:
        report["ok"] = False
        report["error"] = "no source verified; nothing published"
        with open(STATE_FILE, "w") as f:
            json.dump(report, f, indent=1)
        print(json.dumps(report))
        return 1

    try:
        publish(payload)
        report["ok"] = True
        report["published_fields"] = sorted(payload.keys())
    except Exception as e:
        report["ok"] = False
        report["error"] = f"publish failed: {e}"
    with open(STATE_FILE, "w") as f:
        json.dump(report, f, indent=1)
    print(json.dumps(report))
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
