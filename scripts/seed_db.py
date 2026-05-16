#!/usr/bin/env python3
"""
Generates app/src/main/assets/michelin.db from the upstream CSV.

The produced SQLite file matches the Room v2 schema (AppDatabase version = 2):
  - restaurant      (RestaurantEntity)
  - foursquare_cache (FoursquareCacheEntity, empty)
  - visited_restaurant (VisitedRestaurantEntity, empty)

user_version pragma is set to 2 so Room skips migrations on first open.
room_master_table is intentionally omitted — Room creates it on first open.

Usage:
    python3 scripts/seed_db.py [--out app/src/main/assets/michelin.db]
    or via:
    just seed-db
"""

import argparse
import csv
import hashlib
import io
import sqlite3
import sys
import urllib.request
from pathlib import Path

CSV_URL = (
    "https://raw.githubusercontent.com/ngshiheng/michelin-my-maps"
    "/main/data/michelin_my_maps.csv"
)

# Column indices in the upstream CSV (0-indexed, skip header row)
# 0:Name 1:Address 2:Location 3:Price 4:Cuisine 5:Longitude 6:Latitude
# 7:PhoneNumber 8:Url 9:WebsiteUrl 10:Award 11:GreenStar 12:FacilitiesAndServices 13:Description

DDL_RESTAURANT = """
CREATE TABLE IF NOT EXISTS `restaurant` (
    `id` TEXT NOT NULL,
    `name` TEXT NOT NULL,
    `address` TEXT NOT NULL,
    `location` TEXT,
    `latitude` REAL NOT NULL,
    `longitude` REAL NOT NULL,
    `award` TEXT,
    `greenStar` INTEGER NOT NULL,
    `cuisine` TEXT,
    `price` TEXT,
    `phoneNumber` TEXT,
    `url` TEXT NOT NULL,
    `websiteUrl` TEXT,
    `description` TEXT,
    `facilitiesAndServices` TEXT,
    PRIMARY KEY(`id`)
);
"""

DDL_FOURSQUARE_CACHE = """
CREATE TABLE IF NOT EXISTS `foursquare_cache` (
    `restaurantId` TEXT NOT NULL,
    `fsqId` TEXT,
    `photoUrl` TEXT,
    `openingHoursJson` TEXT,
    `phone` TEXT,
    `rating` REAL,
    `fetchedAt` INTEGER NOT NULL,
    PRIMARY KEY(`restaurantId`)
);
"""

# Matches MIGRATION_1_2 SQL exactly
DDL_VISITED = """
CREATE TABLE IF NOT EXISTS visited_restaurant (
    restaurant_id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    award TEXT,
    cuisine TEXT,
    visited_at INTEGER NOT NULL
);
"""


def sha256_prefix(text: str) -> str:
    """Mirrors DatasetSyncWorker.sha256Prefix — first 16 hex chars of SHA-256."""
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    return digest[:16]


def nonempty(s: str) -> str | None:
    v = s.strip()
    return v if v else None


def download_csv(url: str) -> list[list[str]]:
    print(f"Downloading CSV from {url} …", file=sys.stderr)
    with urllib.request.urlopen(url) as resp:
        content = resp.read().decode("utf-8")
    reader = csv.reader(io.StringIO(content))
    rows = list(reader)
    print(f"  {len(rows) - 1} data rows (excl. header)", file=sys.stderr)
    return rows


def parse_rows(rows: list[list[str]]) -> list[tuple]:
    entities = []
    for row in rows[1:]:  # skip header
        if len(row) < 13:
            continue
        url = nonempty(row[8])
        if not url:
            continue
        try:
            lat = float(row[6])
            lon = float(row[5])
        except ValueError:
            continue
        green_star = 1 if row[11].strip().lower() == "true" else 0
        description = nonempty(row[13]) if len(row) > 13 else None
        entities.append((
            sha256_prefix(url),  # id
            row[0].strip(),       # name
            row[1].strip(),       # address
            nonempty(row[2]),     # location
            lat,                  # latitude
            lon,                  # longitude
            nonempty(row[10]),    # award
            green_star,           # greenStar
            nonempty(row[4]),     # cuisine
            nonempty(row[3]),     # price
            nonempty(row[7]),     # phoneNumber
            url,                  # url
            nonempty(row[9]),     # websiteUrl
            description,          # description
            nonempty(row[12]),    # facilitiesAndServices
        ))
    return entities


def build_db(out_path: Path, rows: list[list[str]]) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if out_path.exists():
        out_path.unlink()

    conn = sqlite3.connect(str(out_path))
    try:
        cur = conn.cursor()
        cur.executescript(DDL_RESTAURANT + DDL_FOURSQUARE_CACHE + DDL_VISITED)

        entities = parse_rows(rows)
        cur.executemany(
            """INSERT OR REPLACE INTO restaurant VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            entities,
        )
        conn.commit()

        # Set schema version to 2 so Room skips migrations on first open.
        # Must be done outside a transaction (PRAGMA user_version is not transactional).
        cur.execute("PRAGMA user_version = 2")
        conn.commit()

        cur.execute("SELECT COUNT(*) FROM restaurant")
        count = cur.fetchone()[0]
        print(f"Wrote {count} rows → {out_path}", file=sys.stderr)
        if count < 100:
            print("ERROR: suspiciously few rows — aborting", file=sys.stderr)
            sys.exit(1)
    finally:
        conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out",
        default="app/src/main/assets/michelin.db",
        help="Output path for the SQLite asset",
    )
    args = parser.parse_args()

    rows = download_csv(CSV_URL)
    build_db(Path(args.out), rows)


if __name__ == "__main__":
    main()
