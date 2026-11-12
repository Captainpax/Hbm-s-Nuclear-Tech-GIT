# Moving the port to another PC

Source work is saved on `captainpax/forge-1.20.1` in
`Captainpax/Hbm-s-Nuclear-Tech-GIT`. Clone that branch explicitly: the fork's
`master` remains the original 1.7.10 source.

The unpublished **PC move backup — 2026-11-12** draft in the same fork preserves
both Survival worlds, the multiplayer checkpoint world, both visual QA worlds,
screenshots, test evidence and relevant logs/settings. Draft assets require
signing in as an account with access to the fork's drafts. Its tag includes the
saved source commit; find it with:

```text
gh release list --repo Captainpax/Hbm-s-Nuclear-Tech-GIT
```

Install Git, GitHub CLI, Python 3.11+ and a Java 17 JDK on the new PC. Clone the
port branch, download that draft with `gh release download <backup-tag>`, and
follow the included `RESTORE.md`. Verify and restore the archives with:

```text
python tools/porting/pc_move.py verify --backup <download-directory> --extract .
```

The manifest records the source commit and the size/hash of each archived file.
`SHA256SUMS` covers the ZIPs, development JAR, manifest, restore instructions and
validation log. Extraction checks every file and refuses to overwrite different
contents. Source paths in the archives are relative to the cloned repository.

Saved worlds retain their progress and installed data packs. Resume the existing
Survival sessions with the matching Gradle profile; do not rerun supply or seed
functions. For multiplayer, regenerate the isolated loopback configuration and
RCON password with `multiplayer_smoke.py prepare`, then create a fresh two-client
session with `client_driver.py prepare` before starting either client. Sanitized
old server settings are reference copies under the checkpoint evidence folder.

The backup excludes generated GameTest/geology worlds, build/Gradle caches,
downloaded Minecraft libraries and publishing credentials. Those dependencies
and test worlds can be regenerated. The included mod stays a development build;
the full parity and release gates in the porting notes remain open.

To make another backup, commit and push the reviewed source first, stop the QA
clients/servers, and use a new output directory:

```text
python tools/porting/pc_move.py package --output <backup-directory> --date YYYY-MM-DD --validation <validation-log>
```

Keep backups as unpublished drafts in the Captainpax fork. A migration backup
does not open a PR, update `master`, or publish a finished-mod release.
