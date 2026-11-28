# Hermitic

Native Android client (`com.martronics.hermitic`) for communicating remotely and securely with a self-hosted **Hermes Agent** instance over HTTPS (Cloudflare Tunnel + Cloudflare Access / mTLS).

## Security & Architecture Highlights

- **Strict HTTPS-Only Transport:** `network_security_config.xml` sets `cleartextTrafficPermitted="false"` globally; `http://` URLs are rejected before any request leaves the device.
- **Defense-in-Depth Auth:** Combines Cloudflare Access edge authentication (non-interactive **Service Token** headers and/or **mTLS** Android `KeyChain` client certificate) with Hermes Bearer auth (`API_SERVER_KEY`).
- **On-Device Keystore Vault:** Stores endpoint URL, API keys, and tokens via Android Keystore (`AES256_GCM` / `EncryptedSharedPreferences`), excludes prefs/Room DB from cloud/device backups, supports `BiometricPrompt` app lock, and enforces `FLAG_SECURE` screenshot/recents masking.
- **Streaming + Offline Sync:** Subscribes to `/v1/runs/{run_id}/events` SSE streams with `Idempotency-Key` protection, mid-stream drop reconciliation (`GET /v1/runs/{run_id}`), collapsible tool-call timeline cards, `/stop` cancellation, and inline **Approve / Deny** & **Clarify** controls.

---

## Host Setup Guide (Hermes Agent + Cloudflare Tunnel)

### 1. Enable Hermes API Server (Loopback Only)

In `~/.hermes/.env` on your host machine, bind the API server strictly to loopback (`127.0.0.1`) and set a strong `API_SERVER_KEY`:

```bash
API_SERVER_ENABLED=true
API_SERVER_HOST=127.0.0.1
API_SERVER_PORT=8642
API_SERVER_KEY=replace-with-a-long-random-secret
```

Restart the gateway (`hermes gateway`) and verify locally:

```bash
curl -s http://127.0.0.1:8642/health
```

### 2. Expose via `cloudflared` Tunnel

Configure a Cloudflare Tunnel ingress rule forwarding an HTTPS hostname (e.g. `https://hermes.yourdomain.com`) to `http://127.0.0.1:8642`:

```yaml
tunnel: <your-tunnel-id>
credentials-file: /path/to/<your-tunnel-id>.json

ingress:
  - hostname: hermes.yourdomain.com
    service: http://127.0.0.1:8642
  - service: http_status:404
```

### 3. Lock Down with Cloudflare Access (Non-Interactive Policy)

In **Cloudflare Zero Trust -> Access -> Applications**, protect `hermes.yourdomain.com` with a **Service Auth** policy (never a browser-only login rule, which redirects REST/SSE clients to HTML login pages):

1. Create an **Access Service Token** to obtain `CF-Access-Client-Id` and `CF-Access-Client-Secret`, and/or configure **mTLS** client certificates.
2. Add a **Service Auth** policy allowing your Service Token (and/or valid client certificate).
3. Enter your `https://` URL, `API_SERVER_KEY`, and Cloudflare Service Token / mTLS alias inside Hermitic's **Connection** screen and tap **Test Connection** to verify `/health` and `/v1/capabilities`.

---

## Build & Verify

```bash
./gradlew check assembleDebug assembleRelease
```

## Documentation & Tracking

- **Confluence Space:** `HERM` (`Hermitic - Specification` & `Hermitic - Implementation Plan`)
- **Jira Project:** `HERM` (`HERM-1` Epic, `HERM-2`–`HERM-10`)
