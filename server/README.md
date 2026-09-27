# TopJournal AI server

The server keeps the RuRouter credential on the Windows computer and exposes only a narrow HTTPS endpoint to the Android app.

## Commands

```powershell
powershell -ExecutionPolicy Bypass -File .\server\setup.ps1
powershell -ExecutionPolicy Bypass -File .\server\start.ps1
```

The app connects to `https://<LAN-IP>:8787`. Keep the computer and phone on the same private Wi-Fi network. If Windows Firewall asks about Node.js, allow access only for private networks.

`setup.ps1` stores the provider token with Windows DPAPI, creates a local certificate authority and issues a LAN certificate for the current IPv4 address. Re-run setup after the computer's LAN address changes, then rebuild the APK so it receives the current server URL and public CA certificate.

The server does not persist journal snapshots or AI responses. Requests are limited in size and frequency. The upstream provider token is never included in Android resources, BuildConfig, logs, or responses.

## Homework AI

`POST /api/homework` returns a compact structured result instead of Markdown. It supports contextual actions for one homework item or the current list. The Android client extracts text from PPTX, DOCX, XLSX/ZIP-based office files and plain text, renders PDF pages, and compresses images before sending them. Visual pages are read by the vision model and the final concise answer is produced by GLM 5.3.

You can start the server by double-clicking `Start-AI-Server.cmd`. The DPAPI-protected provider credential must be opened under the same Windows account that ran `server/setup.ps1`.
