# COBOL Dependency Analyzer

This independent clone implements the user mission in `MISSION.md`. Only this
repository may change. The user subsequently authorized configuring origin as
https://github.com/Gustavo2358/cobol-dependency-analyzer.git and publishing the
initial implementation. This supersedes the original local-only restriction
in the historical mission. Further publishing follows explicit user requests.
Reuse the existing frontend and nominal binding. The operational path is
parse -> compact COBOL control -> demand-driven logical values -> dependencies.
Do not add AIR, lowering, physical memory, intermediate serialization, UI,
proof infrastructure, or plugin architecture. Preserve known candidates and
log unknown remainders. Full writes replace old values; queries use BEFORE.
Test every semantic change, including negative cases. Keep JSON minimal,
deterministic and deduplicated by (type,name) per program. Global resource
failure must fail explicitly and leave the old output intact.
Run focused tests while editing, the inherited FAST near stabilization, and
73-program CardDemo plus relevant standalone fixtures before claiming parity.
Old frontend documentation is reference; the mission supersedes its product
boundary and remote-review workflow in this dedicated clone.
