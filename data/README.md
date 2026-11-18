# Data

| File | What | Source |
|---|---|---|
| `celestrak/weather.json` | Weather satellites, OMM JSON | CelesTrak GP group `weather`, snapshot with epochs up to 2026-11-17 |
| `celestrak/iridium.json` | Iridium comms satellites, OMM JSON | CelesTrak GP Iridium group, same snapshot |
| `celestrak/cubesat.json` | CubeSats, OMM JSON | CelesTrak GP group `cubesat`, same snapshot |
| `stations.csv` | Ground station(s) | SatNOGS Network station list (public API) |
| `assumptions.properties` | All made-up numbers | Our own assumptions |
| `priorities.csv` | Optional per-satellite priority overrides | Our own assumptions |

The satellite type comes from the file: `weather` → `WeatherSat`, `iridium` → `CommsSat`,
`cubesat` → `CubeSat`.

## Known limitations of the data
- predict4java reads only the classic TLE format. We convert each OMM JSON record to TLE
  lines (`orbitslot.io.OmmToTleConverter`). TLEs can't hold catalog numbers above 99999,
  so those objects are skipped (in this snapshot: MTG-I2, 100530, which is GEO anyway).
- Only LEO satellites (period < 128 min, i.e. altitude up to ~2000 km) are scheduled. GEO, Molniya and MEO objects are
  excluded and listed at load time.
- The snapshot is fixed for reproducible offline demos. Orbit predictions get less
  accurate the further the planning window is from the epochs (a few days is fine).
