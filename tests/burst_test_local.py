#!/usr/bin/env python3
"""Local-friendly Paytm Seat Reservation correctness/load tester.
Python standard library only. Defaults: 100 requests, 10 workers, 5s timeout.
"""
import argparse
import concurrent.futures
import json
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid


def http_json(method, url, body=None, headers=None, timeout=5):
    data = None
    h = dict(headers or {})
    h.setdefault("Accept", "application/json")
    if body is not None:
        data = json.dumps(body).encode()
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            try:
                payload = json.loads(raw.decode()) if raw else {}
            except Exception:
                payload = {}
            return r.status, payload, time.perf_counter() - start, None
    except urllib.error.HTTPError as e:
        try:
            raw = e.read()
            payload = json.loads(raw.decode()) if raw else {}
        except Exception:
            payload = {}
        return e.code, payload, time.perf_counter() - start, None
    except Exception as e:
        return None, None, time.perf_counter() - start, str(e)


def reserve(base, show, seats, user, idem, timeout):
    return http_json(
        "POST", f"{base}/shows/{show}/reserve",
        {"seats": seats},
        {"Authorization": f"Bearer {user}", "Idempotency-Key": idem},
        timeout
    )


def cancel(base, reservation_id, user, timeout):
    return http_json(
        "POST", f"{base}/reservations/{reservation_id}/cancel",
        headers={"Authorization": f"Bearer {user}"},
        timeout=timeout
    )


def parallel(fn, count, workers):
    workers = min(max(1, workers), count)
    gate = threading.Event()

    def run(i):
        gate.wait()
        return fn(i)

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        fs = [pool.submit(run, i) for i in range(count)]
        gate.set()
        return [f.result() for f in fs]


def percentile(values, p):
    if not values:
        return 0.0
    v = sorted(values)
    x = (len(v) - 1) * p / 100
    lo = int(x)
    hi = min(lo + 1, len(v) - 1)
    return v[lo] + (v[hi] - v[lo]) * (x - lo)


def report(name, results):
    counts = {}
    latencies = []
    network = 0
    for status, _, elapsed, error in results:
        latencies.append(elapsed)
        key = "NETWORK" if error else str(status)
        counts[key] = counts.get(key, 0) + 1
        if error:
            network += 1

    print("\n" + "=" * 68)
    print(name)
    print("=" * 68)
    print("Requests       :", len(results))
    for key in sorted(counts):
        print(f"HTTP {key:<9}: {counts[key]}")
    print("Network errors :", network)
    print("Latency        : avg=%.3fs p50=%.3fs p95=%.3fs max=%.3fs" % (
        statistics.mean(latencies),
        percentile(latencies, 50),
        percentile(latencies, 95),
        max(latencies)
    ))
    return counts, network


def check_app(args):
    status, payload, _, error = http_json(
        "GET", f"{args.base}/shows/{args.show}", timeout=args.timeout
    )
    if status != 200:
        print("Application check failed:", status, error or payload)
        return False
    return True


def hot(args):
    prefix = "local-hot-" + uuid.uuid4().hex[:8]

    def task(i):
        return reserve(
            args.base, args.show, [args.seat],
            f"local-hot-user-{i}",
            f"{prefix}-{i}",
            args.timeout
        )

    results = parallel(task, args.requests, args.workers)
    counts, network = report("HOT-SEAT", results)
    ok = (
        counts.get("201", 0) == 1 and
        counts.get("409", 0) == args.requests - 1 and
        network == 0 and
        sum(v for k, v in counts.items() if k not in ("201", "409")) == 0
    )
    print("INVARIANT      :", "PASS" if ok else "FAIL")
    print(f"Expected       : 1 x 201 + {args.requests - 1} x 409, zero errors")
    return ok


def idempotency(args):
    user = "local-idempotency-user"
    key = "local-idem-" + uuid.uuid4().hex[:12]
    seat = "A13" if args.seat == "A12" else args.seat

    def task(_):
        return reserve(args.base, args.show, [seat], user, key, args.timeout)

    results = parallel(task, args.requests, args.workers)
    counts, network = report("IDEMPOTENCY", results)
    success = counts.get("201", 0) + counts.get("200", 0)
    ok = success >= 1 and network == 0 and counts.get("409", 0) == 0
    print("RESULT         :", "PASS" if ok else "CHECK")
    print("Requirement    : one logical reservation; retries must not duplicate it.")
    return ok


def distributed(args):
    # show-4 has 20,000 seats with rows of 100 (A1..A100, B1..B100, etc.)
    show = "show-4" if args.show == "show-1" else args.show
    seats = [f"A{i}" for i in range(1, args.requests + 1)]

    def task(i):
        return reserve(
            args.base, show, [seats[i]],
            f"local-dist-user-{i}",
            "local-dist-" + uuid.uuid4().hex[:10],
            args.timeout
        )

    results = parallel(task, args.requests, args.workers)
    counts, network = report("DISTRIBUTED CAPACITY", results)
    ok = network == 0 and not any(k.startswith("5") for k in counts)
    print("CLIENT RESULT  :", "PASS" if ok else "CHECK")
    print("Note           : this measures local capacity; HTTP 409 can indicate a seat collision.")
    return ok


def cancellation(args):
    user = "local-cancel-user"
    seat = "A14" if args.seat == "A12" else args.seat
    status, payload, _, error = reserve(
        args.base, args.show, [seat], user,
        "local-cancel-" + uuid.uuid4().hex[:10], args.timeout
    )
    print("\nReservation    :", status)
    if status != 201:
        print("Cannot continue:", error or payload)
        return False

    rid = payload.get("reservation_id")
    if not rid:
        print("reservation_id missing from response")
        return False

    status, payload, _, error = cancel(args.base, rid, user, args.timeout)
    print("Cancellation   :", status)
    ok = status in (200, 204) and error is None
    print("CANCELLATION   :", "PASS" if ok else "FAIL")
    return ok


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--base", default="http://localhost:8080")
    p.add_argument("--scenario", choices=["hot", "idempotency", "distributed", "cancel", "all"], default="all")
    p.add_argument("--requests", type=int, default=100)
    p.add_argument("--workers", type=int, default=10)
    p.add_argument("--timeout", type=float, default=5)
    p.add_argument("--show", default="show-1")
    p.add_argument("--seat", default="A12")
    args = p.parse_args()

    if args.requests < 1 or args.workers < 1:
        p.error("requests and workers must be >= 1")

    print("Paytm Local Test Suite")
    print("Base URL :", args.base)
    print("Scenario :", args.scenario)
    print("Requests :", args.requests)
    print("Workers  :", args.workers)
    print("Timeout  :", args.timeout, "sec")

    if not check_app(args):
        sys.exit(2)

    start = time.perf_counter()
    all_ok = True
    if args.scenario in ("hot", "all"):
        if not hot(args):
            all_ok = False
    if args.scenario in ("idempotency", "all"):
        if not idempotency(args):
            all_ok = False
    if args.scenario in ("distributed", "all"):
        if not distributed(args):
            all_ok = False
    if args.scenario in ("cancel", "all"):
        if not cancellation(args):
            all_ok = False

    print("\nTotal duration: %.2fs" % (time.perf_counter() - start))
    sys.exit(0 if all_ok else 1)


if __name__ == "__main__":
    main()
