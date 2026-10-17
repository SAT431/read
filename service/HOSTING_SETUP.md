# Folio: strictly free hosting setup

**Superseded by the user's direct-Android architecture instruction. Hosting is no longer required.** These steps are historical; use android/DIRECT_PROVIDER_ARCHITECTURE.md instead.

Prepared 14 October 2026. This is a deployment recipe, not an existing live server. Container execution, public HTTPS and live Gemini/Groq calls remain unverified.

## Recommended first attempt

Use an Oracle Cloud **Always Free** Ubuntu VM in your account's home region, with a free DuckDNS hostname and Caddy HTTPS. The Android app connects to this private API; Gemini/Groq keys stay on the server. PDFs, original figure crops and the SQLite reconstruction cache live in a persistent Docker volume.

Oracle's current documentation lists an A1 allowance equivalent to **2 OCPUs / 12 GB RAM**, and **200 GB combined boot/block storage**. For this single-user service, start with one Always Free-labelled A1 VM, 1 OCPU / 6 GB RAM and a 50 GB boot volume, keeping all existing account resources within the total allowance. Use the console's current eligibility and cost estimate as the final check. Do not select paid resources or upgrade to Pay As You Go. Free capacity is not guaranteed and idle VMs may be reclaimed; this option cannot promise continuous availability. See [Oracle's current limits and reclamation policy](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm).

Render Free is unsuitable for this storage design: local PDFs and SQLite disappear on restart/redeploy, and its free services cannot attach persistent disks. See [Render Free limitations](https://render.com/docs/free). If Oracle signup/capacity fails, keep hosting pending and use local development; do not silently switch to a paid service.

## Account and address steps

1. Sign up through [Oracle Cloud Free Tier](https://www.oracle.com/cloud/free/) yourself. Complete any identity/payment-method verification privately. Check that the account stays on its free plan; trial credit is not the same as an Always Free resource.
2. In the home region, create the eligible Ubuntu VM described above. Save the SSH private key privately. Verify the resource estimates before creating it. Do not provision a paid load balancer, managed database or extra volume.
3. Allow inbound TCP 80 and 443 in the VM's cloud network rules and host firewall. Restrict SSH 22 to your own public IP. Do not open API port 8000 publicly.
4. Create a free hostname at [DuckDNS](https://www.duckdns.org/why.jsp) and point it at the VM public IPv4 address. Set no AAAA record unless working IPv6 is configured. Update DNS if the VM IP changes. Keep the DuckDNS token private.
5. Install Docker Engine and the Compose plugin using [Docker's Ubuntu instructions](https://docs.docker.com/engine/install/ubuntu/). Account, region, capacity and VM connectivity must be checked before this step; no account has been created by this recipe.

## Deploy the prepared service

Copy this project's `service/` directory and `android/api-contracts/prompts/` to the VM, preserving their relative paths. No tablet data, Android SDK, APK or provider secrets are needed in the source upload. From the project's root on the VM:

```bash
cd service/deploy
umask 077
cp .env.example .env
nano .env
```

Privately enter FOLIO_HOST, both provider keys and a Gemini model identifier available on your actual free-tier project. Generate a random service token locally on the server (at least 32 printable ASCII characters), for example with `openssl rand -hex 32`, and put it into FOLIO_ACCESS_TOKEN. Do not paste its output into chat. Keep the provider projects on their free tiers; this app's rate limit is not proof of remaining provider quota.

```bash
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
```

Use `sudo docker` if your server account lacks Docker access. Do not run plain `docker compose config`: its expanded output can contain keys. Caddy obtains HTTPS for the configured hostname when DNS and ports are working; certificate state persists in its own volumes. See [Caddy's deployment documentation](https://caddyserver.com/docs/running#docker-compose).

## Verify before connecting the tablet

Open `https://YOUR_HOST/healthz` and confirm a successful health response. An unauthenticated `https://YOUR_HOST/v1/status` must return 401. On Folio's service settings screen, enter only the HTTPS base URL and the **Folio access token**, never Gemini/Groq keys.

Then verify one text command, one short microphone recording/transcription, and one selected PDF page end to end. Check original text/figures against the reconstructed reader; machine pages remain unchecked. Status flags report configuration, not successful provider calls. Provider quota failures must remain visible without a paid fallback.

## Preserve storage

`docker compose down` stops the deployment while preserving named volumes. **Do not use `down -v` or volume pruning**: that removes PDF/cache/certificate data. Before updating, stop the API briefly and make a consistent copy of the `folio_data` volume, then keep a private copy outside this VM. Confirm backup restore before relying on hosting; a volume surviving a container restart does not protect against VM loss/reclamation. Free storage/backup limits still apply.

Current state: prepared files only. Signup, VM availability, ARM container build, HTTPS, backup restore and live provider checks are pending. No spending is authorized.
