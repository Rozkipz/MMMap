#!/usr/bin/env bash
# Downloads michelin-my-maps CSV and converts it to a Room-compatible SQLite file.
# Output: app/src/main/assets/michelin.db
set -euo pipefail

REPO="ngshiheng/michelin-my-maps"
CSV_URL="https://raw.githubusercontent.com/${REPO}/main/data/michelin_my_maps.csv"
DEST="app/src/main/assets/michelin.db"
CURRENT_TAG_FILE=".michelin-db-tag"

# Get latest commit SHA for the CSV (used as version tag)
TAG=$(curl -fsSL "https://api.github.com/repos/${REPO}/commits?path=data/michelin_my_maps.csv&per_page=1" \
  | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0]['sha'][:7])")

CURRENT_TAG=$(cat "$CURRENT_TAG_FILE" 2>/dev/null || echo "")
if [[ "$TAG" == "$CURRENT_TAG" && -f "$DEST" ]]; then
    echo "michelin.db is already up to date ($TAG) — nothing to do."
    exit 0
fi

echo "Downloading michelin_my_maps.csv (${REPO} @ ${TAG})…"
TMP_CSV=$(mktemp --suffix=.csv)
curl -fsSL --progress-bar "$CSV_URL" -o "$TMP_CSV"

echo "Converting CSV → SQLite…"
TMP_DB=$(mktemp --suffix=.db)
python3 << PYTHON
import csv, sqlite3, hashlib, os

src = "$TMP_CSV"
dst = "$TMP_DB"

con = sqlite3.connect(dst)
cur = con.cursor()

# Match Room entity column names exactly
cur.executescript("""
CREATE TABLE IF NOT EXISTS restaurant (
    id                    TEXT PRIMARY KEY NOT NULL,
    name                  TEXT NOT NULL,
    address               TEXT NOT NULL,
    location              TEXT,
    latitude              REAL NOT NULL,
    longitude             REAL NOT NULL,
    award                 TEXT,
    greenStar             INTEGER NOT NULL DEFAULT 0,
    cuisine               TEXT,
    price                 TEXT,
    phoneNumber           TEXT,
    url                   TEXT NOT NULL,
    websiteUrl            TEXT,
    description           TEXT,
    facilitiesAndServices TEXT
);

CREATE TABLE IF NOT EXISTS foursquare_cache (
    restaurantId     TEXT PRIMARY KEY NOT NULL,
    fsqId            TEXT,
    photoUrl         TEXT,
    openingHoursJson TEXT,
    phone            TEXT,
    rating           REAL,
    fetchedAt        INTEGER NOT NULL
);
-- NOTE: do NOT pre-create room_master_table — Room creates it on first open
-- with the correct identity_hash. Pre-creating it with the wrong hash triggers
-- destructive migration and wipes the restaurant data.
""")

with open(src, newline='', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    rows = []
    for row in reader:
        url = row.get('Url') or ''
        rid = hashlib.sha256(url.encode()).hexdigest()[:16]
        try:
            lat = float(row.get('Latitude') or 0)
            lon = float(row.get('Longitude') or 0)
        except ValueError:
            continue
        rows.append((
            rid,
            row.get('Name') or '',
            row.get('Address') or '',
            row.get('Location') or None,
            lat,
            lon,
            row.get('Award') or None,
            1 if str(row.get('GreenStar', '0')).strip() in ('1', 'True', 'true', 'yes') else 0,
            row.get('Cuisine') or None,
            row.get('Price') or None,
            row.get('PhoneNumber') or None,
            url,
            row.get('WebsiteUrl') or None,
            row.get('Description') or None,
            row.get('FacilitiesAndServices') or None,
        ))

cur.executemany("""
    INSERT OR REPLACE INTO restaurant
    (id, name, address, location, latitude, longitude, award, greenStar,
     cuisine, price, phoneNumber, url, websiteUrl, description, facilitiesAndServices)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""", rows)

con.commit()
print(f"Inserted {len(rows)} restaurants")
con.close()
PYTHON

mkdir -p "$(dirname "$DEST")"
mv "$TMP_DB" "$DEST"
rm -f "$TMP_CSV"
echo "$TAG" > "$CURRENT_TAG_FILE"
echo "Done — michelin.db ready at $DEST (tag $TAG)"
