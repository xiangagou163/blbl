# Phone → TV Search Input (Phone QR Input) — Implemented Feature Spec

> **Status: Implemented (2026-10-04).** Sections 1–7 document the "手机扫码输入" / Phone QR Input
> feature as implemented in the external GitHub fork **[`lihuom/blbl`](https://github.com/lihuom/blbl)**.
> Sections 8–10 specify the migration for this repository that preserves the reference feature's
> behavior and interface. It has been implemented and verified with JVM tests, an Android emulator,
> and a physical phone.

All citations below are pinned to fork commit
[`2c3d2469066232a8a5ec017c5e88dce2cf934f46`](https://github.com/lihuom/blbl/commit/2c3d2469066232a8a5ec017c5e88dce2cf934f46)
(the default-branch HEAD of `lihuom/blbl` at the time of this investigation, 2026-10-04). Line
numbers were verified against the raw file contents fetched from GitHub at that commit. Local
code links in sections 8–10 describe the current worktree; Android platform behavior is linked to
Android Developers documentation.

A related but distinct feature, bilibili **TV-app QR Login** (`QrLoginActivity`), is documented
separately at the end of this file for context, since it also renders/polls a QR code but has no
LAN-server behavior and predates the fork (inherited from upstream `cat3399/blbl`).

---

## 1. Feature summary

Source fork's own description (README, "本分支新增功能" table):

> **手机扫码输入** — TV 端一键开启局域网服务（`9529` 端口），用手机扫码打开网页输入关键词，可「填入搜索框」或「直接搜索」，解决遥控器打字痛苦的问题

(English: "Phone QR input — the TV side starts a LAN service on port 9529 with one tap; scanning
the QR with a phone opens a web page to type a keyword, which can be 'filled into the search box'
or 'searched immediately', solving the pain of typing with a remote control.")

— [README.md](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/README.md),
"本分支新增功能" section, and the project's FAQ entry on port `9529` / same-LAN requirement / AP
isolation caveat in the same file.

This is confirmed as **fork-specific** (not inherited from upstream `cat3399/blbl`): the feature's
core file, `PhoneInputServer.kt`, was introduced in fork commit
[`09ddc42`](https://github.com/lihuom/blbl/commit/09ddc42ea2b7b59c6e5b30bcf868859c28d22fe2)
("feat: add phone QR input, local history settings entry, debug visual distinction and search UI
tweaks") and later optimized in
[`d0b044a`](https://github.com/lihuom/blbl/commit/d0b044a0bb2b74a69a918d455588e4d3016e8dbe)
("feat: merge duplicate danmaku with toggle; optimize phone input server").

---

## 2. Exact interaction flow (as implemented in the fork)

1. User opens the TV app's "搜索" (Search) tab.
2. Opening that screen auto-starts the feature — **no explicit user opt-in, button, or settings
   toggle exists** in the fork's source (verified by exhaustive search of `AppPrefs.kt` and the
   `feature/settings` package; no "phoneInput" preference key was found anywhere in the codebase).
   - `SearchFragment.onViewCreated()` constructs a `SearchRenderer` and calls `renderer.setupInput()`
     — [`SearchFragment.kt`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/feature/search/SearchFragment.kt)
   - `setupInput()` ends by calling `setupPhoneInput()` —
     [`SearchRenderer.kt:60`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/feature/search/SearchRenderer.kt#L60)
     (call site at
     [`SearchRenderer.kt:463`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/feature/search/SearchRenderer.kt#L463))
3. `setupPhoneInput()` —
   [`SearchRenderer.kt:470-495`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/feature/search/SearchRenderer.kt#L470-L495):
   - Resolves a LAN IPv4 via `PhoneInputServer.buildUrl()`. If none found, the phone-input panel is
     hidden (`View.GONE`) and nothing starts.
   - Calls `PhoneInputServer.start()` to bind the listening socket. If bind fails, panel stays hidden.
   - Generates a QR bitmap of `http://<lan-ip>:9529/` via `PhoneInputServer.generateQrBitmap(url)`
     and shows it, together with the plain-text URL, in an always-visible panel
     (`panel_phone_input`) next to the on-screen keyboard —
     [`fragment_search.xml:167-200`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/res/layout/fragment_search.xml#L167-L200).
   - Registers a `PhoneInputServer.Listener` that, on receiving input, calls
     `interactor.setQuery(text)` and, if `action == SEARCH`, `interactor.performSearch()`.
4. User's phone scans the QR (using its own camera/QR app — the TV never scans anything) and opens
   the served page in its browser.
5. The phone's page is a single inline HTML document served by the TV itself (textarea + two
   buttons: "投送到搜索框" [fill] / "直接搜索" [search]) —
   [`PhoneInputServer.kt:299-377`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L299-L377).
6. On tapping a button, the page's JS does:
   ```js
   fetch('/input', {
     method: 'POST',
     headers: { 'Content-Type': 'application/json' },
     body: JSON.stringify({ text: text, action: action }) // action: "fill" | "search"
   })
   ```
7. TV-side `handleInput(body)` extracts `text`/`action` using a hand-rolled JSON string scanner (no
   JSON library) —
   [`PhoneInputServer.kt:220-242`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L220-L242),
   then posts to the main thread (`Handler(Looper.getMainLooper())`) to invoke the registered
   listener, which fills and/or triggers the TV search.
8. The HTTP response to the phone is simply `{"ok":true}`; the phone UI shows "已发送" (sent).

---

## 3. Network / server behavior

- **Implementation**: hand-rolled HTTP/1.1 server over raw JDK `ServerSocket`/`Socket` — no
  embedded web-server library (no NanoHTTPD, Ktor, OkHttp-server, etc.) —
  [`PhoneInputServer.kt`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt),
  object declared at
  [`:33`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L33).
- **Port**: fixed at **9529** —
  [`PhoneInputServer.kt:34`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L34).
  A source comment explicitly states the port was chosen to match "common community
  implementations, for ease of memorization" —
  [`:19-24`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L19-L24).
- **Bind address**: `InetAddress.getByName("0.0.0.0")` (all interfaces), backlog 50 —
  [`:65-75`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L65-L75).
- **Threading**: a daemon `accept()` loop thread spawns one daemon worker thread per connection;
  each connection has a 15s socket read timeout —
  [`:67-74`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L67-L74),
  [`:159`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L159).
- **Routes**:
  | Method | Path | Behavior |
  |---|---|---|
  | `GET` | `/` or `/index.html` | Returns inline HTML input page |
  | `POST` | `/input` | Body `{"text":"...","action":"fill"\|"search"}`; responds `{"ok":true}` |
  | `GET` | `/status` | Returns `{"running":true,"port":9529}` |
  | other | * | `404 Not Found` |

  — [`PhoneInputServer.kt:185-200`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L185-L200).
- **Transport**: plain HTTP, no TLS; `Connection: close` per response —
  [`:288-296`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L288-L296).
  The app manifest globally allows cleartext traffic —
  [`AndroidManifest.xml:25`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/AndroidManifest.xml#L25)
  (`android:usesCleartextTraffic="true"`).
- **Encoding handling**: headers are read as ISO-8859-1 (per HTTP spec), then the POST body bytes
  are deliberately re-decoded from Latin-1 back to raw bytes and finally parsed as UTF-8, with an
  explicit code comment explaining this is required to avoid garbling Chinese text —
  [`:234-246`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L234-L246).
- **QR generation**: ZXing `QRCodeWriter`, `UTF-8` charset hint, margin 1, rendered to an
  `RGB_565` bitmap via a batched `setPixels` call (optimized in commit `d0b044a` to avoid ~260k
  individual `setPixel` calls) —
  [`:138-156`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L138-L156).

---

## 4. Permissions / lifecycle

- **Permissions declared by the fork at the cited commit**: `INTERNET`, `ACCESS_NETWORK_STATE`,
  `REQUEST_INSTALL_PACKAGES` —
  [`AndroidManifest.xml:4-6`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/AndroidManifest.xml#L4-L6).
- **Android local-network permission is target-SDK dependent.** Android's current guidance says
  apps targeting below SDK 37 temporarily retain local-network access through an implicit grant
  associated with `INTERNET`; apps targeting SDK 37+ on Android 17 must declare and request
  `ACCESS_LOCAL_NETWORK` at runtime to accept incoming TCP connections. Do not request that
  permission before targeting SDK 37. See [Android local network permission guidance](https://developer.android.com/privacy-and-security/local-network-permission).
- **No camera permission** is needed or used for this feature; the TV only *renders* a QR code,
  it never scans one (scanning happens on the user's phone, outside the app).
- **No feature toggle**: the server auto-starts on Search-screen view creation and has no
  persisted on/off preference anywhere in `AppPrefs.kt` (64 KB file, grepped in full) or
  `feature/settings/*`.
- **Start**: `SearchRenderer.setupInput()` → `setupPhoneInput()` →
  `PhoneInputServer.start()`/`setListener(...)` —
  [`SearchRenderer.kt:470-495`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/feature/search/SearchRenderer.kt#L470-L495).
- **Stop**: only in `SearchRenderer.release()` (`PhoneInputServer.setListener(null)` +
  `PhoneInputServer.stop()`), called from `SearchFragment.onDestroyView()` —
  [`SearchRenderer.kt:672-679`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/feature/search/SearchRenderer.kt#L672-L679).
- **Tab-switch behavior**: `MainActivity.switchRoot()` hides inactive root tab fragments with
  `tx.hide(other)` + `tx.setMaxLifecycle(other, Lifecycle.State.STARTED)` rather than destroying
  their views (fragments are only `tx.remove(other)`'d in a `clearBackStack` path for non-cached
  roots) —
  [`MainActivity.kt:775-811`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/ui/MainActivity.kt#L775-L811).
  **Inference (not runtime-verified)**: because `STARTED` keeps the fragment's view alive
  (`onDestroyView` is not invoked), the LAN server likely continues running while the user
  navigates to other tabs (Home/Category/Dynamic/Live/My) within the same app session, and is
  only torn down when the Search fragment is actually removed or the process dies.

---

## 5. Security limitations (evidence-based)

1. **No authentication, pairing secret, or per-session token** in the QR URL or on `/input`. The
   URL is a bare `http://<lan-ip>:9529/`. Any device already on the same LAN/Wi-Fi — not only the
   one that scanned the QR — can `POST /input` directly (or simply port-scan 9529) and inject
   arbitrary search text or trigger a search, with zero pairing/handshake step.
2. **Cleartext HTTP only** — no TLS. Typed text and the served page traverse the LAN unencrypted
   and are sniffable on shared, open, or compromised Wi-Fi.
3. **Fixed, predictable port (9529)**, chosen explicitly for memorability per the author's own
   comment — this also makes the service trivially discoverable by a simple port scan without ever
   needing to see the QR code.
4. **Bound to all interfaces (`0.0.0.0`)** rather than only the resolved "LAN" interface — if the
   device has other active network paths (e.g., VPN, USB/Wi-Fi tethering, ADB-over-network), the
   server is reachable there too.
5. **Minimal/non-standard body parsing** — the custom JSON extractor does a raw substring scan for
   the first `"text"`/`"action"` keys rather than full JSON parsing/validation —
   [`:247-286`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/phoneinput/PhoneInputServer.kt#L247-L286).
   Blast radius observed in code is limited to populating/triggering a search query; no additional
   command-execution surface was found in this file.
6. **No rate limiting** on `/input` — a LAN peer could spam search triggers.
7. **Long-lived exposure window** — per the lifecycle analysis above, the server likely remains
   bound for the duration of the app session rather than only while the Search screen is the
   foreground/visible screen, widening the window during which the above issues are exploitable.

---

## 6. Related feature for context: bilibili TV-app QR Login (`QrLoginActivity`)

This is a **separate, upstream-inherited** feature (not fork-specific) that also shows/polls a QR
code, included here only to avoid conflation with Phone QR Input. It has **no LAN-server
component** — all network traffic goes to `passport.bilibili.com` over HTTPS.

- Entry point: the unauthenticated "我的" (My) tab shows `MyLoginFragment`; its login button
  launches `QrLoginActivity` —
  [`MyLoginFragment.kt:23`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/feature/my/MyLoginFragment.kt#L23);
  declared in the manifest at
  [`AndroidManifest.xml:48-51`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/AndroidManifest.xml#L48-L51).
- Flow mimics bilibili's official **TV-app** QR login API (distinct from the website login API):
  `startFlow()` POSTs a signed request to
  `https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code` (app-key/secret pair for
  `android_hd`, MD5-signed via `AppSigner`), renders the returned `url` as a QR (ZXing
  `MultiFormatWriter`), then polls
  `https://passport.bilibili.com/x/passport-tv-login/qrcode/poll` every 2s —
  [`QrLoginActivity.kt:105-160`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/feature/login/QrLoginActivity.kt#L105-L160).
- Poll result codes handled: `86101`/`86039` (waiting for scan), `86090` (scanned, awaiting phone
  confirmation), `86038` (QR expired, polling stops), `0` (success) —
  [`:182-224`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/feature/login/QrLoginActivity.kt#L182-L224).
  On success, `access_token`/`refresh_token` are persisted into a `BiliAppAuthSession` —
  [`BiliAppAuthSession.kt`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/prefs/BiliAppAuthSession.kt) —
  and any `cookie_info.cookies` are copied into the app's cookie jar.
- Session storage note: tokens are stored in plaintext inside
  `SharedPreferences("blbl_prefs", Context.MODE_PRIVATE)` —
  [`AppPrefs.kt:16`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/java/blbl/cat3399/core/prefs/AppPrefs.kt#L16) —
  with no additional at-rest encryption, and the app declares `android:allowBackup="true"` with no
  custom backup-exclusion rules —
  [`AndroidManifest.xml:18`](https://github.com/lihuom/blbl/blob/2c3d2469066232a8a5ec017c5e88dce2cf934f46/app/src/main/AndroidManifest.xml#L18).
- Git history shows `QrLoginActivity.kt` dates back to upstream `cat3399` commits (e.g.
  ["支持多账号登录"](https://github.com/lihuom/blbl/commit/b668b83caa69274fab949a54e58e2fa3eea170ca),
  2026-05-04), predating the fork's phone-input work — i.e., this is **not** a fork-introduced
  feature.

---

## 7. Open questions / gaps (not resolved by source reading alone)

- Whether the LAN server truly stays bound for extended periods while backgrounded was inferred
  from fragment-lifecycle code, not confirmed via a running device/instrumented test; some TV OEM
  task-killers may reap the process sooner.
- No in-app settings toggle to disable the feature was found; this was verified by exhaustive grep
  of `AppPrefs.kt` and `feature/settings/*`, but a toggle implemented purely through
  not-yet-reviewed resource/XML preference screens cannot be 100% ruled out from Kotlin source
  alone.
- Authorship of the fork's phone-input commits (`09ddc42`, `d0b044a`) is attributed to a generic
  `ci`/`example@example.com` git identity in the fork's history, rather than a named individual —
  true authorship beyond the `lihuom` GitHub account could not be independently verified from
  primary sources.

---

## 8. Current local-repository integration points

Surveyed on 2026-10-04. The existing search screen already has a query field, a D-pad keyboard,
suggestions/history, and an explicit search action. `SearchFragment` creates the renderer and
interactor; `SearchInteractor.setQuery()` updates the query without searching, while
`performSearch()` runs the established search flow and records history according to the existing
history policy:

- [SearchFragment.kt](../../app/src/main/java/blbl/cat3399/feature/search/SearchFragment.kt#L35)
- [SearchRenderer.kt](../../app/src/main/java/blbl/cat3399/feature/search/SearchRenderer.kt#L58)
- [SearchRenderer.kt query input](../../app/src/main/java/blbl/cat3399/feature/search/SearchRenderer.kt#L358)
- [SearchInteractor.kt setQuery](../../app/src/main/java/blbl/cat3399/feature/search/SearchInteractor.kt#L104)
- [SearchInteractor.kt performSearch](../../app/src/main/java/blbl/cat3399/feature/search/SearchInteractor.kt#L139)
- [fragment_search.xml](../../app/src/main/res/layout/fragment_search.xml#L21)

ZXing is already a dependency and the separate login screen already renders QR codes, so QR
generation can reuse existing project capability. That login QR is unrelated to phone input:

- [app/build.gradle.kts](../../app/build.gradle.kts#L138)
- [QrLoginActivity.kt](../../app/src/main/java/blbl/cat3399/feature/login/QrLoginActivity.kt#L261)

The app currently targets SDK 36 and declares `INTERNET` / `ACCESS_NETWORK_STATE`; its manifest
also allows cleartext traffic. The only existing `ServerSocket` helper found for this survey binds
to `127.0.0.1` on an ephemeral port, so it is not a LAN listener and must not be reused unchanged:

- [app/build.gradle.kts target SDK](../../app/build.gradle.kts#L22)
- [AndroidManifest.xml network permissions](../../app/src/main/AndroidManifest.xml#L4)
- [AndroidManifest.xml cleartext policy](../../app/src/main/AndroidManifest.xml#L27)
- [DashLocalHttpProxy.kt](../../app/src/main/java/blbl/cat3399/feature/player/engine/DashLocalHttpProxy.kt#L70)

## 9. Migration specification: preserve the reference design

### Problem statement

Typing search terms with a TV remote is inconvenient. The reference feature lets a viewer use a
phone browser to enter a keyword and either fill the TV's search box or immediately search.

### Solution and interaction

1. Opening the Search screen starts the phone-input service automatically, matching the fork; do
   not add a settings toggle or a separate start/stop action.
2. When a local IPv4 address is available and the service binds successfully, show the QR code and
   readable URL (`http://<lan-ip>:9529/`) beside the TV on-screen keyboard.
3. The user scans the QR with the phone's camera and uses the served web page. The page has a text
   area and two actions: **投送到搜索框** (fill) and **直接搜索** (search).
4. The phone sends UTF-8 JSON to `POST /input`, with `text` and `action` (`fill` or `search`).
   Filling updates the existing search query; searching updates it and invokes the existing search
   flow. The phone receives `{"ok":true}` and shows **已发送**.
5. The page is served inline by the TV. `GET /` and `GET /index.html` return the page;
   `GET /status` returns running/port status; unsupported routes return 404.

### Implementation decisions

- Keep the reference network behavior: plain HTTP, fixed port 9529, a listener bound to
  `0.0.0.0`, and its existing start/stop lifecycle. Start during Search input setup and stop when
  the Search renderer is released as its fragment view is destroyed. Since the app caches root
  tabs, the service may remain active while Search is hidden, matching the reference lifecycle.
- Keep the QR panel visible alongside the on-screen keyboard. If no local IPv4 is resolved or the
  fixed port cannot be bound, hide the phone-input panel as in the reference.
- Preserve the existing Search flow: fill through `SearchInteractor.setQuery()`; direct search
  through `setQuery()` and `performSearch()`. Do not add a separate history-writing path.
- Reuse the existing QR-generation library and the Search screen's existing query/interactor
  behavior. Keep the existing bilibili QR Login flow separate and unchanged.
- Preserve the source protocol and interaction rather than adding a new pairing or session model:
  no authentication token, dynamic port, HTTPS, rate limiter, per-phone session, opt-in toggle, or
  settings entry is part of this migration.
- The current app already declares `INTERNET` and allows cleartext traffic. Keep the current
  manifest/target configuration unchanged for this feature.

### User stories

1. As a TV viewer, I want phone input to be available automatically on the Search screen, so that
   I do not need to configure or start another service.
2. As a TV viewer, I want to see a QR code and its URL beside the on-screen keyboard, so that I can
   connect a phone without typing the TV's address.
3. As a phone user, I want to scan the QR code with my camera and open a browser page, so that I
   do not need to install a companion app.
4. As a phone user, I want to type Chinese or Latin search text, so that I can enter search terms
   more easily than with a TV remote.
5. As a phone user, I want **投送到搜索框** to fill the TV query without searching, so that I can
   continue using the TV search controls.
6. As a phone user, I want **直接搜索** to submit the TV query, so that I can start browsing
   results from the phone.
7. As a phone user, I want the page to confirm that a query was sent, so that I know the TV
   received my action.
8. As a TV viewer, I want filled text and direct searches to use the existing search behavior, so
   that suggestions, result navigation, and search-history policy stay consistent.
9. As a phone user, I want to send another query while the Search screen remains alive, so that I
   do not need to rescan the QR for every search.
10. As a TV viewer, I want the server to stop when the Search view is destroyed, so that its
    lifecycle matches the Search feature.
11. As a user on a network that isolates Wi-Fi clients, I want the QR flow to depend on both
    devices being mutually reachable, so that I understand why scanning may not connect.
12. As a returning viewer, I want phone input to remain separate from bilibili QR Login, so that
    entering a search term cannot alter my account session.

### Testing decisions

- Good tests exercise the public behavior: HTTP route and response, UTF-8 text delivery, the two
  actions, and service cleanup. Do not test the hand-written JSON scanner or thread layout as
  implementation details.
- Test the phone-input server through its existing listener and HTTP routes, then verify that fill
  and direct-search actions reach the existing Search behavior.
- Validate the visible QR/page flow on an Android TV device or emulator with a phone on the same
  reachable Wi-Fi network. Check fill, direct search, repeated submissions, and Search-view
  destruction.
- Preserve existing history-policy coverage; prior art includes the pure JVM history-policy tests.
  The repository currently has JVM unit tests but no Android instrumentation test source set.

### Out of scope

- Changing the reference's network security model or adding authentication, pairing, rate limits,
  HTTPS, dynamic ports, a settings toggle, or a user-controlled start/stop flow.
- Search-by-pasted-BV/av-link parsing, phone-based navigation or playback control, and a native
  phone companion app.
- Changing the bilibili account QR Login flow or search-history policy.

### Further notes

- The reference service is unauthenticated plain HTTP on a fixed port and accepts submissions from
  reachable devices on its LAN. That behavior is being documented for parity, not as a security
  endorsement; search text is visible to observers on an untrusted Wi-Fi network.
- The fork documents that Wi-Fi client/AP isolation can prevent the phone from reaching the TV.
- The current app targets SDK 36. If its target SDK is later raised to 37, Android's [local-network
  permission guidance](https://developer.android.com/privacy-and-security/local-network-permission)
  requires a separate compatibility update for accepting incoming TCP connections.
