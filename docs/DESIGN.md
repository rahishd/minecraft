# Cydev Mod - Design Decisions

Target: Minecraft 1.21.11 Java Edition, Fabric, Java 21 (runs on JDK 25).
Concept: a Cities: Skylines II style systems simulation inside Minecraft.

## Decisions
- Scope: full simulation, all 8 phases.
- Citizens: simulated records plus visible villager entities.
- Buildings: players build their own; government projects (roads, pipelines,
  public buildings) are built by the Mayor's administration.
- Server-side simulation, works in single-player and multiplayer.
- Sim logic is plain Java (no Minecraft types); `world/` holds the adapters.

## Government
- Mayor elected by all citizens; anyone may file candidacy.
- Tenure: 1 real-life month (24 x 30 days = 720 h = 51,840,000 ticks).
  Use wall-clock time, not game ticks.
- Mayor score / progress visible to everyone (HUD + screen).
- Mayor appoints advisors, secretary and the Council (Office of the Mayor).
- Permits: construction, water pipelines and other works need Mayor approval.
  Mayor may inspect, stop or ban works within their administrative area.

## Expansion / federation
- Mayor can select an area to carve out a new city and call an election there;
  the original mayor stays mayor of the original city
  (e.g. Mayor of ABC, Mayor of XYZ).
- Mayors can discuss further expansion and form a federation of cities.

## Phases
1 Foundation  2 Roads+zoning  3 Utilities  4 Demand/growth
5 Citizens+economy  6 Traffic  7 Services  8 UI+polish
Government/election/expansion runs alongside phases 1-5.
