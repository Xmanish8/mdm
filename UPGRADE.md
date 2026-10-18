# Use this production-preparation package

Extract MDM-production.zip into a new folder such as E:\projects\MDM-production and open its pom.xml as an existing project. Keep MDM-enhanced as a backup. No new Spring Initializr project is needed.

Local development still uses start.ps1 and your existing opsflow database. Production Docker uses a separate mdm database volume and has demo data disabled. It does not automatically move or erase your local data.

Read DEPLOYMENT.md before running Compose. The domain is undecided, so the shipped .env contains blank non-secret settings. scripts/setup-production.ps1 prompts for your chosen deployment details and creates private random secret files. No real secret is included in the ZIP.

Do not expose development port 8080/8181 publicly. Production is designed to expose only the HTTPS proxy on 80/443.
