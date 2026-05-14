<div align="center">

<img src="docs/banner.svg" alt="MMMap" width="860">

[![CI](https://github.com/Rozkipz/MMMap/actions/workflows/ci.yml/badge.svg)](https://github.com/Rozkipz/MMMap/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/Rozkipz/MMMap/badges/coverage.json)](https://github.com/Rozkipz/MMMap/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

**Browse every restaurant in the MICHELIN Guide — offline, on an interactive map.**

[Download APK](https://github.com/Rozkipz/MMMap/releases/latest) · [Report a bug](https://github.com/Rozkipz/MMMap/issues) · [Request a feature](https://github.com/Rozkipz/MMMap/issues)

</div>

---

## Features

- **Offline-first** — full MICHELIN dataset bundled in the APK, no sign-in required
- **Filter** by award (★★★ / ★★ / ★ / Bib Gourmand / Selected), cuisine, and price tier
- **Near Me** — list of restaurants sorted by walking distance
- **Rich detail** — photos, opening hours, phone number, and website via Foursquare
- **Deep links** directly to each restaurant's MICHELIN Guide page
- **Automatic updates** — dataset syncs in the background every 24 hours

## Screenshots

> _Screenshots coming soon — contributions welcome!_
>
> To add screenshots, place `map.png`, `filters.png`, `detail.png`, and `nearby.png`
> in `docs/screenshots/` and open a pull request.

## Install

### Download (sideload)
1. Download `app-release.apk` from the [latest release](https://github.com/Rozkipz/MMMap/releases/latest)
2. On your phone: **Settings → Security → Install unknown apps** → allow your browser
3. Open the downloaded APK and tap **Install**

### Build from source
See [Development setup](#development-setup) below.

---

## Development setup

### Requirements

| Tool | Version |
|------|---------|
| Android Studio | Ladybug or newer |
| JDK | 17 |
| [`just`](https://github.com/casey/just) | any recent |

Install `just`:
```bash
cargo install just   # or: brew install just
```

Set `JAVA_HOME` to JDK 17 if it is not your system default:
```bash
export JAVA_HOME=/path/to/jdk-17
```

### API keys

Create `local.properties` (gitignored — never commit this):
```
fsq.api.key=YOUR_FOURSQUARE_API_KEY
```

Get a free key at [developer.foursquare.com](https://developer.foursquare.com/).
Place enrichment (photos, hours, phone) is disabled but the map works without a key.

### Common commands

```bash
just build          # compile debug APK
just install        # install to connected device/emulator
just run            # install + launch
just test           # unit tests
just coverage       # unit tests + JaCoCo coverage report
just lint           # Android lint
just check          # lint + tests (run before committing)
```

---

## Contributing

Contributions are welcome. Please:

1. Open an issue first for anything beyond a small fix
2. Fork the repo and create a feature branch (`git checkout -b feat/my-change`)
3. Keep commits focused — one logical change per commit
4. Run `just check` before pushing; CI enforces lint and 75% coverage on new code
5. Open a pull request against `main`

There are no CLA or sign-off requirements.

---

## Tech stack

| Layer | Library |
|-------|---------|
| UI | Jetpack Compose + Material 3 |
| Map | MapLibre Native Android |
| Database | Room (SQLite) |
| Networking | Retrofit + OkHttp |
| DI | Hilt |
| Background sync | WorkManager |
| Place enrichment | Foursquare Places API |

All dependencies are either Apache 2.0, MIT, or LGPL-licensed — fully F-Droid-compatible.

---

## Roadmap

- [ ] F-Droid / IzzyOnDroid distribution
- [ ] Offline tile bundles for full offline use
- [ ] Country selector to narrow the dataset

---

## License

This project is licensed under the **GNU General Public License v3.0** — see [`LICENSE`](LICENSE) for details.

## Attribution

| Source | Licence |
|--------|---------|
| Restaurant data: [MICHELIN Guide](https://guide.michelin.com) via [ngshiheng/michelin-my-maps](https://github.com/ngshiheng/michelin-my-maps) | MIT |
| Place enrichment: [Foursquare Places API](https://developer.foursquare.com/) | Foursquare ToS |
| Map tiles: [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) | ODbL |
