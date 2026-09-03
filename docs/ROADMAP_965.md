# doFast — roadmapa 965 punktów

Stan roboczy: 2026-09-02. Bazowy snapshot listy został porównany z aktualnym kodem i historią GitHuba. Ten dokument zachowuje wszystkie punkty 1–965 dokładnie raz i nie oznacza jako ukończonych elementów wymagających prawdziwego Stripe, środowiska staging/production, decyzji prawnych ani dostępu do ustawień repozytorium.

- Audytowany code baseline: `837cbe7769842e9007b9088f8255a03febadada2` (merge PR #230).
- Najnowszy pakiet: [PR #230](https://github.com/jedreekPrograms/dofast/pull/230) — scalony merge commitem po zielonym exact-head gate; post-merge gate zweryfikowany przed publikacją tego dokumentu.
- Ochrona gałęzi: `master protected=false`, required checks wyłączone, brak rulesetów.
- Otwarty techniczny priorytet: końcowy authorization/privacy/IDOR sweep (#615, #618, #620, #957).

## Legenda i liczby

| Status | Znaczenie | Liczba |
|---|---|---:|
| ✅ | zaimplementowane / zweryfikowane w repo | 612 |
| 🟢 | główna część domknięta, jawnie opisany zewnętrzny blocker | 2 |
| 🟡 | częściowe albo aktywnie audytowane | 86 |
| 🔴 | niewykonane, przyszłościowe lub zależne od zewnętrznego dostępu/decyzji | 265 |
| **Razem** |  | **965** |

## Co doszło od poprzedniego snapshotu

| Obszar | Dostarczone PR-y |
|---|---|
| Neutralne odpowiedzi i scoped lookups dla prywatnych zasobów | #182–#197, #205–#206, #209–#210, #214–#217 |
| Realtime, auth-version, expiry/rotation tokenów i revocation sesji | #198–#199, #203–#204 |
| Finansowe fail-closed i race safety | #201–#202, #207, #211, #213 |
| Persisted-identity gates w prywatnych usługach | #215, #218–#230 |

PR-y #181, #186, #190, #208 i #212 zostały zamknięte bez merge jako duplikaty/superseded i nie są liczone jako dostarczone zmiany.

## Blocker wymagający dostępu

Punkty #50–#54 i #956 nadal są czerwone. Kod i workflowy istnieją, ale sam GitHub nie wymusza PR-ów ani zielonych checków. Do domknięcia trzeba wejść w ustawienia repozytorium i utworzyć branch protection/ruleset dla `master` (PR required, required checks, bez force-push i bez usuwania gałęzi). Aktualna integracja GitHub udostępnia tylko odczyt tych ustawień, a próba otwarcia ustawień w przeglądarce nie uzyskała działającej sesji.

## Pełne 965 punktów — zweryfikowany stan

1.  ✅ Profesjonalna struktura monorepo. 
2.  ✅ `apps/api` jako osobny backend. 
3.  ✅ `apps/web` jako osobny frontend. 
4.  ✅ `infra/` jako osobna infrastruktura. 
5.  ✅ `docs/` jako dokumentacja architektury i procesów. 
6.  ✅ Docker dla developmentu. 
7.  ✅ Docker Compose dla pełnego lokalnego stacku. 
8.  ✅ Production-oriented Compose. 
9.  ✅ Nginx jako gateway. 
10.  ✅ Healthcheck PostgreSQL. 
11.  ✅ Healthcheck API. 
12.  ✅ Healthcheck frontend/Nginx. 
13.  ✅ Startup ordering kontenerów. 
14.  ✅ Java 21. 
15.  ✅ Spring Boot 4.1.x. 
16.  ✅ React 19 + Vite. 
17.  ✅ PostgreSQL zamiast starej MariaDB. 
18.  ✅ PostGIS. 
19.  ✅ `pg_trgm`. 
20.  ✅ Flyway jako jedyny właściciel migracji DB. 
21.  ✅ Modularny monolit zamiast przypadkowej struktury. 
22.  ✅ Rozdzielenie domen `user`. 
23.  ✅ Rozdzielenie domen `job`. 
24.  ✅ Rozdzielenie domen `location`. 
25.  ✅ Rozdzielenie domen `wallet`. 
26.  ✅ Rozdzielenie domen `payment`. 
27.  ✅ Rozdzielenie domen `payout`. 
28.  ✅ Rozdzielenie domen `chat`. 
29.  ✅ Rozdzielenie domen `notification`. 
30.  ✅ Rozdzielenie domen `review`. 
31.  ✅ Rozdzielenie domen `dispute`. 
32.  ✅ Rozdzielenie domen `verification`. 
33.  ✅ DTO zamiast zwracania encji JPA. 
34.  ✅ Service layer jako właściciel use-case'ów. 
35.  ✅ Repozytoria przypisane do domen. 
36.  ✅ Transakcje dla operacji biznesowych. 
37.  ✅ Pessimistic locking dla krytycznych race condition. 
38.  ✅ `@Version` jako dodatkowy optimistic protection. 
39.  ✅ Walidacje DB przez constraints. 
40.  ✅ Sekrety poza repo. 
41.  ✅ `.env.example`. 
42.  ✅ Production profile fail-closed. 
43.  ✅ CI na GitHub Actions. 
44.  ✅ Maven verify w CI. 
45.  ✅ Frontend dependency audit. 
46.  ✅ Frontend lint. 
47.  ✅ Frontend production build. 
48.  ✅ Runtime container smokes. 
49.  ✅ Production Compose contract smoke. 
50.  🔴 GitHub branch protection/ruleset dla `master`. 
51.  🔴 Wymuszenie PR przed merge do `master`. 
52.  🔴 Wymuszenie required checks przed merge. 
53.  🔴 Zablokowanie force-push do `master`. 
54.  🔴 Zablokowanie usuwania `master`. 
55.  ✅ CodeQL/SAST. 
56.  ✅ Container vulnerability scanning. 
57.  ✅ SBOM. 
58.  ✅ Third-party GitHub Actions supply-chain pinning/guard. 
59.  ✅ Automatyczne dependency updates przez Dependabot. 
60.  ✅ Role `USER` i `ADMIN`. 
61.  ✅ Status `ACTIVE`. 
62.  ✅ Status `SUSPENDED`. 
63.  ✅ Publiczna rejestracja nie może stworzyć ADMIN. 
64.  ✅ Bezpieczne hashowanie haseł. 
65.  ✅ Password login. 
66.  ✅ JWT access token. 
67.  ✅ Krótki TTL access tokenu. 
68.  ✅ Limit maksymalnego TTL JWT. 
69.  ✅ Access token tylko w pamięci frontendu. 
70.  ✅ Brak JWT w `localStorage`. 
71.  ✅ Refresh session w DB — rotacja access tokenów i unieważnianie po zmianie hasła/suspension domknięte m.in. przez #198, #199, #203 i #204.
72.  ✅ HttpOnly refresh cookie. 
73.  ✅ Osobny CSRF cookie. 
74.  ✅ `X-CSRF-Token`. 
75.  ✅ Hash refresh tokenu w DB. 
76.  ✅ Hash CSRF tokenu w DB. 
77.  ✅ Refresh token rotation. 
78.  ✅ Session family. 
79.  ✅ Refresh reuse detection. 
80.  ✅ Grace window na równoległy refresh. 
81.  ✅ Logout. 
82.  ✅ Revoke sesji przy zmianie hasła. 
83.  ✅ `auth_version`. 
84.  ✅ Natychmiastowe unieważnianie starych JWT. 
85.  ✅ Natychmiastowe blokowanie zawieszonego konta. 
86.  ✅ Google Sign-In. 
87.  ✅ Weryfikacja Google ID token po stronie backendu. 
88.  ✅ Google `sub` jako trwała tożsamość. 
89.  ✅ Bezpieczne reguły auto-linkowania Google. 
90.  ✅ Apple Sign-In. 
91.  ✅ Apple `state`. 
92.  ✅ Apple `nonce`. 
93.  ✅ Jednorazowy Apple login challenge. 
94.  ✅ Server-side Apple code exchange. 
95.  ✅ Apple ES256 client secret. 
96.  ✅ Walidacja Apple JWK. 
97.  🟡 Explicit account linking dla Apple. 
98.  ✅ Forgot password. 
99.  ✅ Generyczna odpowiedź bez account enumeration. 
100.  ✅ Jednorazowy reset token. 
101.  ✅ Tylko hash reset tokenu w DB. 
102.  ✅ TTL reset tokenu. 
103.  ✅ Unieważnianie poprzednich reset linków. 
104.  ✅ Password reset. 
105.  ✅ Reset unieważnia aktywne sesje. 
106.  ✅ Reset zwiększa `auth_version`. 
107.  ✅ SMTP delivery AFTER\_COMMIT. 
108.  ✅ Password-recovery cooldown. 
109.  ✅ Email verification. 
110.  ✅ Email verification smoke. 
111.  🟡 Zarządzanie aktywnymi urządzeniami/sesjami w UI — backend rotacji i revocation jest wzmocniony; ekran użytkownika nadal otwarty.
112.  🔴 MFA dla zwykłych userów. 
113.  🔴 MFA/2FA obowiązkowe dla adminów. 
114.  🔴 WebAuthn/security keys dla adminów. 
115.  ✅ IP-aware rate limiting auth. 
116.  ✅ Rate limit logowania. 
117.  ✅ Rate limit rejestracji. 
118.  ✅ Rate limit forgot password. 
119.  ✅ Rate limit reset password. 
120.  ✅ Rate limit Google/Apple auth. 
121.  ✅ Rate limit refresh flow. 
122.  ✅ Bezpieczne traktowanie forwarded IP. 
123.  ✅ Publiczny profil użytkownika. 
124.  ✅ Nickname. 
125.  ✅ Bio. 
126.  ✅ Publiczna lokalizacja tekstowa. 
127.  ✅ `memberSince`. 
128.  ✅ Rating average. 
129.  ✅ Review count. 
130.  ✅ Completed jobs count. 
131.  ✅ Identity verified badge. 
132.  ✅ Profil nie pokazuje e-maila. 
133.  ✅ Profil nie pokazuje walleta. 
134.  ✅ Profil nie pokazuje exact location. 
135.  ✅ Profil nie pokazuje danych płatniczych. 
136.  ✅ Profil nie pokazuje KYC internals. 
137.  ✅ Edycja własnego profilu. 
138.  ✅ Specjalizacje użytkownika. 
139.  ✅ Maksymalnie 10 specjalizacji. 
140.  ✅ Tylko aktywne leaf categories. 
141.  ✅ Trust cards w job details. 
142.  ✅ Trust cards w chat. 
143.  🟡 Zdjęcie/avatar profilu. 
144.  🟡 Finalny public-profile privacy audit — ekspozycja kont zawieszonych i publicznych recenzji domknięta przez #196, #197 i #205; końcowy sweep nadal trwa.
145.  ✅ Kategorie zleceń. 
146.  ✅ Parent categories. 
147.  ✅ Leaf categories. 
148.  ✅ Stabilne slugi. 
149.  ✅ Aktywacja/dezaktywacja kategorii. 
150.  ✅ Podstawowy lifecycle joba. 
151.  ✅ `OPEN`. 
152.  ✅ `IN_PROGRESS`. 
153.  ✅ `COMPLETION_REQUESTED`. 
154.  ✅ `DONE`. 
155.  ✅ `CANCELLED`. 
156.  ✅ `DISPUTED`. 
157.  ✅ Requester nie może przyjąć własnego zlecenia. 
158.  ✅ Tylko OPEN można przyjąć. 
159.  ✅ Tylko assigned worker może zgłosić wykonanie. 
160.  ✅ Requester potwierdza wykonanie. 
161.  ✅ Escrow release dopiero po prawidłowym lifecycle. 
162.  ✅ `INSTANT` assignment mode. 
163.  ✅ `PROPOSALS` assignment mode. 
164.  ✅ Domyślne proste zlecenie „kto pierwszy ten bierze”. 
165.  ✅ Możliwość wyboru konkretnego wykonawcy. 
166.  ✅ Proposal od workera. 
167.  ✅ Prywatność proposalów. 
168.  ✅ Konkurenci nie widzą ofert innych workerów. 
169.  ✅ Worker widzi tylko swoją propozycję. 
170.  ✅ Requester widzi kandydatów. 
171.  ✅ Withdraw proposal. 
172.  ✅ Reject pozostałych proposals po wyborze. 
173.  ✅ Price negotiation jako osobny przełącznik. 
174.  ✅ Fixed-price proposals. 
175.  ✅ Negotiated-price proposals. 
176.  ✅ `INSTANT + negotiation` zabronione. 
177.  ✅ Zwiększenie escrow przy droższej propozycji. 
178.  ✅ Refund nadmiaru escrow przy tańszej propozycji. 
179.  ✅ Funding preflight przed zaakceptowaniem droższej propozycji. 
180.  ✅ Wallet może pokryć część różnicy. 
181.  ✅ Stripe może pokryć brakującą część. 
182.  ✅ Stripe webhook nie wybiera workera. 
183.  ✅ Ponowna autorytatywna walidacja proposal przy accept. 
184.  ✅ Race protection proposal/accept. 
185.  ✅ Blokady użytkowników sprawdzane ponownie przed accept. 
186.  ✅ Publiczne `GET /jobs`. 
187.  ✅ Wyszukiwanie po tekście. 
188.  ✅ Filter po kategorii. 
189.  ✅ Filter `minPrice`. 
190.  ✅ Filter `maxPrice`. 
191.  ✅ Pagination. 
192.  ✅ Stabilny własny pagination DTO. 
193.  ✅ Text search po title. 
194.  ✅ Text search po description. 
195.  ✅ Text search po public location. 
196.  ✅ Text search po destination label. 
197.  ✅ Trigram indexes. 
198.  ✅ Publiczne nearby discovery.
199.  ✅ PostGIS radius query. 
200.  ✅ `ST_DWithin`. 
201.  ✅ `ST_Distance`. 
202.  ✅ GiST geo indexes. 
203.  ✅ Nearby category filtering. 
204.  ✅ Public discovery nie pokazuje exact coordinates. 
205.  ✅ Public discovery rate limiting. 
206.  ✅ Saved jobs. 
207.  ✅ Idempotent save. 
208.  ✅ Idempotent unsave. 
209.  ✅ Batch saved-status. 
210.  ✅ Pagination saved jobs. 
211.  ✅ Usuwanie stale bookmarków. 
212.  ✅ Zakaz bookmarkowania własnego joba. 
213.  ✅ Saved searches. 
214.  ✅ Saved search po query. 
215.  ✅ Saved search po kategorii. 
216.  ✅ Saved search po cenie. 
217.  ✅ Saved search z prywatnym punktem. 
218.  ✅ Saved search z promieniem. 
219.  ✅ Limit 20 presetów. 
220.  ✅ Prywatne coordinates saved search. 
221.  ✅ Recommendation feed „Dla Ciebie”. 
222.  ✅ Matching po specjalizacjach. 
223.  ✅ Wykluczenie własnych jobów. 
224.  ✅ Wykluczenie blocked users. 
225.  🟡 Bardziej zaawansowany ranking rekomendacji. 
226.  🔴 Ranking po predicted earnings/time. 
227.  🔴 Ranking po odległości i dostępności. 
228.  🔴 Learning/personalization na historii zachowania. 
229.  ✅ Point-to-point jobs. 
230.  ✅ On-site jobs. 
231.  ✅ Multi-stop routes. 
232.  ✅ Route quotes. 
233.  ✅ Quote należy do konkretnego usera. 
234.  ✅ Quote ma TTL. 
235.  ✅ Quote jest single-use. 
236.  ✅ Browser nie ustala authoritative distance. 
237.  ✅ Browser nie ustala authoritative ETA. 
238.  ✅ Google Routes API provider. 
239.  ✅ Deterministic dev routing provider. 
240.  ✅ Osobny server Google API key. 
241.  ✅ Osobny browser Google Maps key. 
242.  ✅ Routing provider rate limiting. 
243.  ✅ Weighted provider-cost limiter. 
244.  ✅ Limiter po authenticated user ID. 
245.  ✅ Exact route participant-only. 
246.  ✅ Live courier tracking. 
247.  ✅ Worker publikuje GPS. 
248.  ✅ Requester odczytuje tracking. 
249.  ✅ Outsider nie odczytuje tracking. 
250.  ✅ Tylko bieżąca lokalizacja zamiast GPS trail. 
251.  ✅ GPS accuracy. 
252.  ✅ Heading. 
253.  ✅ Speed. 
254.  ✅ Capture time. 
255.  ✅ Server receive time. 
256.  ✅ Remaining distance. 
257.  ✅ Remaining ETA. 
258.  ✅ Route phase. 
259.  ✅ `TO_ORIGIN`. 
260.  ✅ `TO_DESTINATION`. 
261.  ✅ `ARRIVED_DESTINATION`. 
262.  ✅ Server-side minimum GPS interval. 
263.  ✅ Browser GPS throttling. 
264.  ✅ GPS stale detection. 
265.  ✅ Max GPS accuracy guard. 
266.  ✅ Implied speed guard. 
267.  ✅ Out-of-order GPS rejection. 
268.  ✅ ETA refresh po czasie. 
269.  ✅ ETA refresh po odpowiednim movement. 
270.  ✅ Provider call poza główną GPS transakcją. 
271.  ✅ Stary provider response nie nadpisze nowego GPS. 
272.  ✅ Checkpoint proximity verification. 
273.  ✅ Fresh GPS wymagany do checkpoint. 
274.  ✅ Arrival-radius enforcement. 
275.  ✅ Intermediate checkpoints. 
276.  ✅ Final destination checkpoint. 
277.  ✅ Dotarcie do B nie oznacza completion joba. 
278.  ✅ Final arrival zatrzymuje GPS sharing. 
279.  ✅ Czyszczenie precise live tracking po terminal state. 
280.  ✅ DB trigger jako defense in depth dla live location. 
281.  ✅ Retencja exact origin/destination — mechanizm purge wdrożony. 
282.  ✅ Retencja private location labels — purge wdrożony. 
283.  ✅ Retencja exact route snapshots/geometry. 
284.  ✅ Techniczna polityka anonimizacji historycznej lokalizacji. 
285.  🟡 Finalna prawna/GDPR decyzja o długości retention window. 
286.  ✅ Browser live tracking. 
287.  🔴 Native background GPS. 
288.  🔴 Android client. 
289.  🔴 iOS client. 
290.  ✅ Chat persistent w DB. 
291.  ✅ Chat realtime. 
292.  ✅ WebSocket/STOMP. 
293.  ✅ Tylko uczestnicy joba mają dostęp do chatu. 
294.  ✅ Chat counterpart trust card. 
295.  ✅ Persisted notifications. 
296.  ✅ Notification centre. 
297.  ✅ Unread count. 
298.  ✅ Mark notification read. 
299.  ✅ Mark all read. 
300.  ✅ Realtime notifications. 
301.  ✅ Notification realtime preferences. 
302.  ✅ Critical notifications nie mogą być wyciszone. 
303.  🔴 Browser Web Push. 
304.  🔴 Firebase/Android push. 
305.  🔴 APNs/iOS push. 
306.  🔴 SMS notifications. 
307.  ✅ Authenticated STOMP limiter. 
308.  ✅ WebSocket handshake rate limit. 
309.  ✅ WebSocket concurrent connection limit. 
310.  ✅ SockJS ingress limits. 
311.  ✅ WebSocket body limits/timeouts. 
312.  ✅ STOMP subscription authorization. 
313.  ✅ Client nie może publikować bezpośrednio do `/topic`. 
314.  ✅ Client nie może publikować do `/queue`. 
315.  ✅ Client SEND fail-closed. 
316.  ✅ Real WebSocket ingress smoke. 
317.  🟡 Shared/distributed WebSocket rate limiting przy multi-node. 
318.  ✅ Attachments do joba. 
319.  ✅ Zdjęcia listy zakupów. 
320.  ✅ Zdjęcia produktów. 
321.  ✅ PDF instructions. 
322.  ✅ Receipt attachments. 
323.  ✅ Temporary execution credentials/karty. 
324.  ✅ `JOB_VIEWERS`. 
325.  ✅ `PARTICIPANTS`. 
326.  ✅ `EXECUTION_SECRET`. 
327.  ✅ Worker nie może sam stworzyć execution secret. 
328.  ✅ Execution secret tylko podczas `IN_PROGRESS`. 
329.  ✅ Worker może uploadować participant evidence. 
330.  ✅ Max file size. 
331.  ✅ Max files/job. 
332.  ✅ JPEG. 
333.  ✅ PNG. 
334.  ✅ WebP. 
335.  ✅ PDF. 
336.  ✅ Blokowanie SVG/HTML/executables. 
337.  ✅ Magic-byte file validation. 
338.  ✅ Filename normalization. 
339.  ✅ `Content-Disposition: attachment`. 
340.  ✅ `Cache-Control: no-store`. 
341.  ✅ `nosniff`. 
342.  ✅ Private filesystem storage. 
343.  ✅ AES-256-GCM encryption at rest. 
344.  ✅ Random nonce. 
345.  ✅ Storage abstraction/interface. 
346.  🟡 Antivirus/malware scanning. 
347.  🟡 S3 adapter. 
348.  🟡 MinIO/object-storage adapter. 
349.  🔴 Encryption key rotation. 
350.  🔴 Versioned encryption keys. 
351.  ✅ `expenseBudget`. 
352.  ✅ Oddzielenie wynagrodzenia od kosztów zakupów. 
353.  ✅ Osobny expense escrow. 
354.  ✅ Brak platform fee od zwracanych kosztów. 
355.  ✅ `EXPENSE_BUDGET_LOCK`. 
356.  ✅ `EXPENSE_REIMBURSEMENT`. 
357.  ✅ `EXPENSE_BUDGET_REFUND`. 
358.  ✅ Receipt-backed claims. 
359.  ✅ Worker musi być assigned. 
360.  ✅ Claim tylko podczas `IN_PROGRESS`. 
361.  ✅ Claim musi wskazywać właściwy receipt. 
362.  ✅ Ten sam receipt nie może być użyty dwa razy. 
363.  ✅ Claim nie może przekroczyć expense budget. 
364.  ✅ Receipt staje się trwałym evidence. 
365.  ✅ Immutable expense claims. 
366.  ✅ Expense settlement przy completion. 
367.  ✅ Worker dostaje zaakceptowane wydatki. 
368.  ✅ Requester odzyskuje unused budget. 
369.  ✅ Conservation invariant expense escrow. 
370.  ✅ Expense-aware dispute resolution. 
371.  ✅ Admin może zatwierdzić tylko część kosztów. 
372.  🟡 Structured shopping-list model. 
373.  🔴 `ShoppingItem`. 
374.  🔴 Ilości produktów. 
375.  🔴 Substitution allowed. 
376.  🔴 Bought/unavailable states. 
377.  🔴 Per-item actual price. 
378.  ✅ Wallet. 
379.  ✅ Immutable/auditable wallet ledger. 
380.  ✅ Auditable wallet transactions. 
381.  ✅ Decimal money types. 
382.  ✅ Brak floatów dla pieniędzy. 
383.  ✅ Wallet source-of-funds. 
384.  ✅ Funding lots. 
385.  ✅ Funding movements. 
386.  ✅ `STRIPE_PAYMENT`. 
387.  ✅ `EARNED_JOB`. 
388.  ✅ `LEGACY_UNVERIFIED`. 
389.  ✅ `PLATFORM_ADJUSTMENT`. 
390.  ✅ Stripe-funded balance nie jest withdrawable. 
391.  ✅ Earnings są withdrawable. 
392.  ✅ Funding provenance invariant. 
393.  ✅ `wallet.balance == SUM(remaining funding lots)`. 
394.  ✅ Fail-closed przy rozjechaniu provenance. 
395.  ✅ Preferowane wydawanie non-withdrawable przed earnings. 
396.  ✅ Restore dokładnego źródła środków. 
397.  ✅ Escrow. 
398.  ✅ `HELD`.
399.  ✅ `RELEASED`. 
400.  ✅ `REFUNDED`. 
401.  ✅ Full funding przed publikacją. 
402.  ✅ Idempotent escrow operations. 
403.  ✅ Locking escrow przy settlement. 
404.  ✅ Job publication payment flow. 
405.  ✅ Wallet + Stripe mogą razem sfinansować job. 
406.  ✅ Private `job_publications`. 
407.  ✅ Niedofinansowany job nie trafia do discovery. 
408.  ✅ Publication request id. 
409.  ✅ Publication payload fingerprint. 
410.  ✅ Publication idempotency. 
411.  ✅ Publication wallet reservation. 
412.  ✅ Payment window/expiry. 
413.  ✅ Cancel pending publication. 
414.  ✅ Late Stripe payment handling. 
415.  ✅ Late payment nie wskrzesza anulowanego joba. 
416.  ✅ Recovery reasons. 
417.  ✅ Stripe Payment Element. 
418.  ✅ Automatic payment methods. 
419.  ✅ Redirect methods. 
420.  ✅ Wallet return flow. 
421.  ✅ Publication return flow. 
422.  ✅ Proposal-payment return flow. 
423.  ✅ Browser status nie jest payment authority. 
424.  ✅ Signed webhook jest payment authority. 
425.  ✅ Client-secret query scrub. 
426.  ✅ Nginx nie loguje query string z Stripe secretami. 
427.  ✅ `Referrer-Policy`. 
428.  ✅ Stripe webhook signature verification. 
429.  ✅ PaymentIntent idempotency. 
430.  ✅ Stripe event idempotency. 
431.  ✅ Purpose validation. 
432.  ✅ Amount validation. 
433.  ✅ Currency validation. 
434.  ✅ Metadata validation. 
435.  ✅ Platform fee. 
436.  ✅ Default 1%. 
437.  ✅ Fee basis points configuration. 
438.  ✅ Fee snapshot per escrow. 
439.  ✅ Zmiana fee nie zmienia starych jobów. 
440.  ✅ Fee rounding. 
441.  ✅ Worker net payout. 
442.  ✅ Oddzielny platform revenue ledger. 
443.  ✅ Revenue idempotency. 
444.  ✅ Revenue reconciliation. 
445.  ✅ Stripe refund requests. 
446.  ✅ Full refund. 
447.  ✅ Partial refund. 
448.  ✅ Refund request idempotency. 
449.  ✅ Wallet reserve przed provider refund. 
450.  ✅ Refund tied to exact PaymentIntent. 
451.  ✅ Refund nie może użyć pieniędzy z innego PaymentIntent. 
452.  ✅ Failed refund restore do tego samego source lot. 
453.  ✅ Refund webhook handling. 
454.  ✅ Refund ordering. 
455.  ✅ Same-second conflicting refund events → review. 
456.  ✅ Chargeback/dispute ledger Stripe. 
457.  ✅ `charge.dispute.*`. 
458.  ✅ Provider identity validation. 
459.  ✅ Out-of-order Stripe dispute event protection. 
460.  ✅ Same-second terminal state protection. 
461.  ✅ Chargeback exposure. 
462.  ✅ Partial immediate wallet recovery. 
463.  ✅ Outstanding exposure. 
464.  ✅ Blokowanie outgoing wallet operations przy exposure. 
465.  ✅ Scheduled recovery późniejszych środków. 
466.  ✅ Funds reinstatement. 
467.  ✅ Reinstatement dokładnie wcześniej odzyskanych środków. 
468.  🔴 Automatyczne Stripe dispute evidence submission. 
469.  🟡 Chargeback operator runbook. 
470.  ✅ Worker payouts. 
471.  ✅ Payout eligibility. 
472.  ✅ Payout tylko z withdrawable earnings. 
473.  ✅ Payout request idempotency. 
474.  ✅ `PAYOUT_RESERVE`. 
475.  ✅ `PAYOUT_RESTORE`. 
476.  ✅ User może anulować queued payout. 
477.  ✅ Payout audit trail. 
478.  ✅ `REQUESTED`. 
479.  ✅ `PROCESSING`. 
480.  ✅ `SUBMITTED`. 
481.  ✅ `PAID`. 
482.  ✅ `FAILED`. 
483.  ✅ `REVIEW_REQUIRED`. 
484.  ✅ Ambiguous provider result fail-closed. 
485.  ✅ Stripe Connect account mapping. 
486.  ✅ Stripe Express onboarding. 
487.  ✅ KYC required przed onboardingiem/payoutem. 
488.  ✅ Fresh Stripe account-state check. 
489.  ✅ Manual connected-account payout schedule. 
490.  ✅ Platform Transfer. 
491.  ✅ Connected-account Payout. 
492.  ✅ Osobne idempotency key dla Transfer. 
493.  ✅ Osobne idempotency key dla Payout. 
494.  ✅ Trwałe Stripe provider references. 
495.  ✅ Submitted payout settlement. 
496.  ✅ Signed payout webhooks. 
497.  ✅ Payout reconciliation. 
498.  ✅ Reconciliation nie dispatchuje ponownie payoutu. 
499.  ✅ Reconciliation leasing. 
500.  ✅ Webhook/reconciliation race safety. 
501.  ✅ Transfer reversal przy failed payout. 
502.  ✅ Wallet restore dopiero po bezpiecznym odzyskaniu provider funds. 
503.  ✅ Payout webhook ordering. 
504.  ✅ Preflight przed zewnętrznym transfer reversal. 
505.  🔴 **Real Stripe Connect test-mode full E2E.** 
506.  🔴 Real Express account test. 
507.  🔴 Real test Transfer. 
508.  🔴 Real test connected Payout. 
509.  🔴 Real test payout webhook. 
510.  🔴 Real test payout reconciliation. 
511.  🔴 Real failed-payout reversal test z prawdziwym Stripe test mode. 
512.  ✅ Direct cancellation OPEN job. 
513.  ✅ Refund escrow po OPEN cancellation. 
514.  ✅ Negotiated cancellation dla active job. 
515.  ✅ Cancellation request. 
516.  ✅ Counterparty approval. 
517.  ✅ Counterparty decline. 
518.  ✅ Withdrawal requestu. 
519.  ✅ Twórca requestu nie może go sam zatwierdzić. 
520.  ✅ Tylko jeden pending cancellation request. 
521.  ✅ Approved cancellation zatrzymuje tracking. 
522.  ✅ Approved cancellation refunduje escrow. 
523.  ✅ Expense claims blokują prostą active cancellation. 
524.  ✅ Dispute zamiast kasowania istniejących kosztów. 
525.  ✅ Job disputes. 
526.  ✅ Participant może otworzyć dispute. 
527.  ✅ Dispute tylko dla aktywnego zaakceptowanego joba. 
528.  ✅ Escrow pozostaje HELD podczas dispute. 
529.  ✅ `DISPUTED` blokuje normalne completion/cancellation. 
530.  ✅ Admin dispute queue. 
531.  ✅ Admin claim. 
532.  ✅ `UNDER_REVIEW`. 
533.  ✅ `RELEASE_TO_WORKER`. 
534.  ✅ `REFUND_TO_REQUESTER`. 
535.  ✅ `RESUME_JOB`. 
536.  ✅ Dispute audit events. 
537.  ✅ Only one active dispute/job. 
538.  ✅ Dispute concurrency protection. 
539.  ✅ Expense evidence w admin dispute. 
540.  ✅ Approved expense amount. 
541.  ✅ Reviews. 
542.  ✅ Rating użytkownika. 
543.  ✅ Review count. 
544.  ✅ Review notifications. 
545.  ✅ Public reputation. 
546.  ✅ User blocking. 
547.  ✅ Blocked users page. 
548.  ✅ Blocking wpływa na recommendations. 
549.  ✅ Blocking wpływa na proposals. 
550.  ✅ Blocking wpływa na public job visibility/actions. 
551.  ✅ Job reports. 
552.  ✅ Structured report reasons. 
553.  ✅ Optional report note. 
554.  ✅ Max jeden report/user/job. 
555.  ✅ Reporter nie może zgłosić własnego joba. 
556.  ✅ Report jest private moderation data. 
557.  ✅ Reporter może withdraw pending report. 
558.  ✅ Withdrawal zachowuje historię. 
559.  ✅ Admin report queue. 
560.  ✅ `REVIEWED`. 
561.  ✅ `DISMISSED`. 
562.  ✅ Moderator notes. 
563.  ✅ Moderation optimistic locking. 
564.  ✅ Explicit job enforcement. 
565.  ✅ `CANCEL_OPEN_JOB`. 
566.  ✅ Enforcement osobno od moderation decision. 
567.  ✅ Enforcement audit. 
568.  ✅ Active jobs chronione przed prostym admin cancel. 
569.  ✅ Account enforcement. 
570.  ✅ `SUSPEND_JOB_OWNER`. 
571.  ✅ Target wynika z reported job. 
572.  ✅ Admin nie może suspendować admina tym flow. 
573.  ✅ Safety check active jobs przed suspension. 
574.  ✅ Suspension kasuje pozostałe OPEN listings. 
575.  ✅ Suspension natychmiast odcina auth. 
576.  ✅ Immutable account-enforcement audit. 
577.  ✅ Reactivation tylko przez osobny admin flow. 
578.  ✅ Powód reactivation obowiązkowy. 
579.  ✅ Reactivation audit. 
580.  🟡 Granular admin RBAC. 
581.  🔴 `MODERATOR`. 
582.  🔴 `FINANCE_ADMIN`. 
583.  🔴 `KYC_REVIEWER`. 
584.  🔴 `SUPPORT`. 
585.  🔴 `SUPER_ADMIN`. 
586.  ✅ Identity verification domain. 
587.  ✅ `PENDING`. 
588.  ✅ `VERIFIED`. 
589.  ✅ `REJECTED`. 
590.  ✅ `REVOKED`. 
591.  ✅ Identity verification audit. 
592.  ✅ Admin approval. 
593.  ✅ Admin rejection. 
594.  ✅ Admin revoke. 
595.  ✅ User resubmission. 
596.  ✅ Publicznie tylko verified boolean. 
597.  ✅ Brak skanów dokumentów w naszej DB. 
598.  ✅ Brak numerów dokumentów w naszej DB.
599.  ✅ Brak selfie/biometrii w naszej DB. 
600.  🟡 Zewnętrzny automatyczny KYC provider. 
601.  🟡 Provider webhook signatures/idempotency dla KYC. 
602.  🔴 Finalna biznesowo-prawna decyzja KYC. 
603.  ✅ Admin dashboard. 
604.  ✅ Admin disputes UI. 
605.  ✅ Admin reports UI. 
606.  ✅ Admin payouts UI. 
607.  ✅ Admin verification UI. 
608.  🟡 Admin UX final polish. 
609.  ✅ Security headers w gateway baseline. 
610.  ✅ Production CORS allowlist. 
611.  ✅ Ograniczony actuator. 
612.  ✅ Brak stack trace'ów w publicznych errorach. 
613.  ✅ Authentication server-side. 
614.  ✅ Authorization server-side. 
615.  🟡 **Pełny endpoint-by-endpoint authorization audit — aktywnie trwa; PR-y #178–#234 domknęły kolejne IDOR-y, scoped lookups i fail-closed identity boundaries.**
616.  🟡 Authorization matrix anonymous/user/requester/worker/admin. 
617.  🟡 Blocked-user matrix audit. 
618.  🟡 **Historical-resource privacy audit — RouteQuote, payout, expense, attachments, chat, tracking, exact location, proposals, disputes, publications i refundy mają kolejne scoped/neutral-not-found granice; końcowy sweep nadal trwa.**
619.  🟡 Admin-data exposure audit — kolejka dispute wymusza autoryzację ADMIN również w serwisie (#216); pozostałe powierzchnie admina wymagają końcowego sweepu.
620.  🟡 **IDOR audit — szeroki pakiet #178–#230 zmerge’owany; nadal nie oznacza finalnego sign-off całego API.**
621.  🟡 Mass-assignment audit. 
622.  🟡 DTO sensitive-field audit. 
623.  🟡 Rate-limit wszystkich kosztownych authenticated endpointów. 
624.  🟡 Distributed/shared rate limiting. 
625.  🔴 Redis/shared limiter przy multi-node. 
626.  🟡 WAF/API gateway przed dużą skalą. 
627.  ✅ CI auth smoke. 
628.  ✅ CI password recovery smoke. 
629.  ✅ CI email verification smoke. 
630.  ✅ CI job lifecycle smoke. 
631.  ✅ CI cancellation smoke. 
632.  ✅ CI discovery smoke. 
633.  ✅ CI nearby/PostGIS smoke. 
634.  ✅ CI multi-stop smoke. 
635.  ✅ CI on-site smoke. 
636.  ✅ CI attachment smoke. 
637.  ✅ CI expense smoke. 
638.  ✅ CI publication funding smoke. 
639.  ✅ CI proposal funding smoke. 
640.  ✅ CI payment ledger smoke. 
641.  ✅ CI platform fee smoke. 
642.  ✅ CI Stripe refund smoke. 
643.  ✅ CI Stripe chargeback smoke. 
644.  ✅ CI payout smoke. 
645.  ✅ CI WebSocket ingress smoke. 
646.  ✅ CI reviews/trust smoke. 
647.  ✅ CI identity verification smoke. 
648.  ✅ CI production Compose contract. 
649.  🟡 Load tests. 
650.  🔴 Realistic concurrent-user load test. 
651.  🔴 WebSocket load test. 
652.  🔴 Tracking load test. 
653.  🔴 PostGIS search load test. 
654.  🔴 DB connection-pool saturation test. 
655.  🔴 Stripe webhook burst test. 
656.  🔴 Long-running soak test. 
657.  ✅ Production Compose secrets fail-closed. 
658.  ✅ Production secure cookies. 
659.  ✅ Production SMTP required. 
660.  ✅ Production Stripe secrets required. 
661.  ✅ Production Google Routes key. 
662.  ✅ Production attachment encryption key. 
663.  ✅ Persistent PostgreSQL volume. 
664.  ✅ Persistent attachment volume. 
665.  ✅ HTTP internal Nginx baseline. 
666.  🟡 Real HTTPS edge configuration. 
667.  🔴 Full staging on real hostname. 
668.  🟡 Real production-like WebSocket over HTTPS test. 
669.  🟡 Real Google OAuth staging test. 
670.  🟡 Real Apple OAuth staging test. 
671.  🟡 Real Stripe redirect staging test. 
672.  🟡 Real SMTP deliverability test. 
673.  🔴 SPF configuration. 
674.  🔴 DKIM configuration. 
675.  🔴 DMARC configuration. 
676.  🟡 Email bounce handling. 
677.  🟡 Email delivery monitoring. 
678.  🔴 Off-host PostgreSQL backup. 
679.  🔴 Automatic database backup schedule. 
680.  🔴 Pełne szyfrowanie całego backup bundle/DB dump. 
681.  🔴 Backup retention policy. 
682.  🔴 Attachment off-host backup. 
683.  🟡 Attachment backup consistency — checksums/restore proof istnieją, pełna off-host consistency nadal otwarta. 
684.  🔴 Secure backup encryption key. 
685.  🔴 Secure backup Stripe/config secrets. 
686.  ✅ Real DB restore drill. 
687.  ✅ Real attachment restore drill. 
688.  🟡 Restore + encryption-key validation. 
689.  🔴 RPO definition. 
690.  🔴 RTO definition. 
691.  🔴 Disaster recovery runbook. 
692.  🔴 Server-loss scenario. 
693.  🔴 DB-corruption scenario. 
694.  🔴 Lost-container scenario. 
695.  🔴 Lost-secret scenario. 
696.  🔴 Stripe webhook outage scenario. 
697.  🔴 Production release runbook. 
698.  🔴 Migration runbook. 
699.  🔴 Rollback policy. 
700.  🔴 Forward-fix policy dla nieodwracalnych Flyway migration. 
701.  🔴 Deployment smoke checklist. 
702.  🔴 Emergency rollback checklist. 
703.  🟢 Health/Actuator observability baseline. 
704.  🟡 Centralized logs. 
705.  🟡 Metrics collection. 
706.  🟡 Grafana/dashboard lub odpowiednik. 
707.  🟡 Error tracking. 
708.  🔴 Alert przy finance reconciliation failure. 
709.  🔴 Alert przy payout `REVIEW_REQUIRED`. 
710.  🔴 Alert przy długo wiszącym `SUBMITTED` payout. 
711.  🔴 Alert przy chargeback outstanding exposure. 
712.  🔴 Alert przy webhook failure. 
713.  🔴 Alert przy SMTP failure. 
714.  🔴 Alert przy scheduler failure. 
715.  🔴 Alert przy backup failure. 
716.  🔴 Alert przy health degradation. 
717.  ✅ Finance reconciliation backend logic. 
718.  ✅ Wallet reconciliation. 
719.  ✅ Source-of-funds reconciliation. 
720.  ✅ Stripe-purpose reconciliation. 
721.  ✅ Platform revenue reconciliation. 
722.  ✅ Payout provider identity checks. 
723.  🟡 Operator-friendly reconciliation dashboard. 
724.  🟡 Incident workflow dla finance mismatch. 
725.  ✅ **Final financial crash-window audit — główne boundary #726–#734 domknięte, a #174–#177 dodatkowo utwardziły response anomalies i trwały Stripe retry clock.** 
726.  ✅ PaymentIntent creation crash analysis/recovery. 
727.  ✅ Publication settlement crash analysis + real PostgreSQL rollback proof. 
728.  ✅ Proposal funding crash analysis. 
729.  ✅ Refund dispatch crash analysis. 
730.  ✅ Transfer dispatch crash analysis. 
731.  ✅ Payout creation crash analysis. 
732.  ✅ Transfer reversal crash analysis. 
733.  ✅ Webhook transaction crash analysis. 
734.  ✅ Scheduler crash/restart analysis. 
735.  🟡 Finance chaos tests — część realnych crash/rollback i scheduler-isolation scenarios już istnieje, pełny chaos suite nadal otwarty. 
736.  🟡 Documentation consistency audit. 
737.  🟡 Usunięcie starych nieaktualnych komentarzy o wallet provenance. 
738.  🟡 Sync wszystkich docs z najnowszymi migracjami. 
739.  🟡 Dokumentacja wszystkich endpointów. 
740.  🟡 OpenAPI/Swagger jako pełny publiczny kontrakt. 
741.  🟡 Customer-facing help docs. 
742.  🟡 Admin/operator docs. 
743.  🟡 Privacy retention documentation — exact-location część znacznie domknięta. 
744.  🔴 Regulamin doFast. 
745.  🔴 Privacy Policy. 
746.  🔴 Cookie Policy, jeśli będzie wymagana dla finalnego stacku. 
747.  🔴 Globalna data-retention policy. 
748.  🔴 GDPR user-data export. 
749.  🔴 GDPR account deletion workflow. 
750.  🔴 Reguły przechowywania financial records po account deletion. 
751.  🔴 Reguły przechowywania disputes/reviews. 
752.  🟡 Reguły przechowywania exact addresses — techniczny purge gotowy, finalny okres wymaga decyzji prawnej. 
753.  🔴 Reguły przechowywania receipts. 
754.  🔴 Reguły przechowywania KYC status metadata. 
755.  🔴 Legal review modelu marketplace. 
756.  🔴 Legal review przepływu środków. 
757.  🔴 Legal review Stripe Connect/KYC. 
758.  🔴 Legal review platform fee. 
759.  🔴 Tax/accounting model. 
760.  🔴 VAT/accounting documents. 
761.  🔴 Faktury/platform-fee documents. 
762.  🔴 Accounting export. 
763.  🔴 Payout accounting. 
764.  🔴 Refund accounting. 
765.  🔴 Chargeback accounting. 
766.  🔴 Prohibited tasks policy. 
767.  🔴 Prohibited goods policy. 
768.  🔴 Dangerous-task policy. 
769.  🔴 Cash-transfer-job policy. 
770.  🔴 User age policy. 
771.  🔴 Decyzja: czy nieletni mogą wykonywać zadania. 
772.  🔴 Parental consent flow, jeżeli nieletni będą dopuszczeni. 
773.  🔴 Age verification, jeżeli wymagane. 
774.  🔴 Phone verification. 
775.  🟡 Anti-fraud scoring. 
776.  🟡 Velocity rules finansowe. 
777.  🟡 Suspicious-account detection. 
778.  🟡 Device/session risk signals — auth-version, WebSocket revalidation, access-token expiry/rotation i suspension revocation działają; pełny risk scoring urządzeń nadal otwarty.
779.  🟡 Multiple-account abuse detection. 
780.  🟡 Payout fraud controls. 
781.  🟡 High-value job limits. 
782.  🟡 New-account payout limits. 
783.  🟡 Manual review thresholds. 
784.  🔴 doFast promo/coupon engine. 
785.  🔴 Promo codes. 
786.  🔴 First-order discount. 
787.  🔴 Referral program. 
788.  🔴 Campaign limits. 
789.  🔴 Coupon abuse protection. 
790.  🔴 Merchant/partner promotions. 
791.  ✅ Execution-secret loyalty-card attachments. 
792.  🔴 Structured loyalty-card integration. 
793.  🟡 Final frontend design-system pass. 
794.  🟡 Responsive mobile web audit. 
795.  🟡 Accessibility audit. 
796.  🟡 Keyboard navigation. 
797.  🟡 Screen-reader labels. 
798.  🟡 Contrast audit.
799.  🟡 Loading skeleton consistency. 
800.  🟡 Empty-state consistency. 
801.  🟡 Error-state consistency. 
802.  🟡 Confirmation flow consistency. 
803.  🟡 Destructive-action UX. 
804.  🟡 Payment UX review. 
805.  🟡 Dispute UX review. 
806.  🟡 Tracking mobile UX review. 
807.  🟡 Admin UX review. 
808.  🟡 Migrate frontend do TypeScript — opcjonalne. 
809.  🔴 Native Android app. 
810.  🔴 Native iOS app. 
811.  🔴 Native secure credential storage. 
812.  🔴 Native camera attachment flow. 
813.  🔴 Native background GPS. 
814.  🔴 Native push notifications. 
815.  🔴 Deep links. 
816.  🔴 Native maps/navigation. 
817.  🔴 App Store release flow. 
818.  🔴 Google Play release flow. 
819.  🔴 Advanced matching domain. 
820.  🔴 Availability system wykonawców. 
821.  🔴 Online/offline worker state. 
822.  🔴 Worker working radius. 
823.  🔴 Working hours. 
824.  🔴 Transport mode preferences. 
825.  🔴 Intelligent recommendation scoring. 
826.  🔴 Predicted ETA-to-job. 
827.  🔴 Predicted worker suitability. 
828.  🔴 Search ranking. 
829.  🔴 Trending jobs. 
830.  🔴 Personalized notification alerts. 
831.  🔴 Automatic saved-search push. 
832.  🔴 Geofenced new-job alerts. 
833.  🟡 Final security review — aktywny authorization/privacy sweep doszedł do PR #230; branch protection i zewnętrzne testy nadal blokują sign-off.
834.  🟡 Final privacy review — szeroki historical-resource/IDOR sweep jest mocno zaawansowany, lecz niezamknięty.
835.  🟡 Final authorization review — repozytoryjne scoped lookups i fail-closed identity są systematycznie rozszerzane; końcowa macierz endpointów nadal otwarta.
836.  🟢 Final financial review — crash-window część zakończona; real Stripe E2E nadal blockerem. 
837.  🟡 Final abuse review. 
838.  🔴 External penetration test — idealnie przed większym launch. 
839.  🔴 Real staging environment. 
840.  🔴 Staging data reset strategy. 
841.  🔴 Staging Stripe test environment. 
842.  🔴 Staging OAuth credentials. 
843.  🔴 Staging SMTP. 
844.  🔴 Staging Google Maps. 
845.  🔴 Staging monitoring. 
846.  🔴 Full staging E2E happy path. 
847.  🔴 Full staging cancellation path. 
848.  🔴 Full staging dispute path. 
849.  🔴 Full staging refund path. 
850.  🔴 Full staging payout path. 
851.  🔴 Full staging chargeback simulation. 
852.  🔴 Full staging backup/restore. 
853.  🔴 Full staging server restart during transactions. 
854.  🔴 Full staging deployment rollback. 
855.  🔴 Pilot-user operational playbook. 
856.  🔴 Customer support workflow. 
857.  🔴 Dispute SLA. 
858.  🔴 Verification SLA. 
859.  🔴 Payout review SLA. 
860.  🔴 Chargeback response SLA. 
861.  🔴 Incident severity levels. 
862.  🔴 Security incident response plan. 
863.  🔴 Data-breach response plan. 
864.  🔴 Financial incident response plan. 
865.  🔴 Production secrets rotation process. 
866.  🔴 Stripe key rotation process. 
867.  🔴 JWT secret rotation strategy. 
868.  🔴 Attachment key rotation strategy. 
869.  🔴 OAuth credential rotation strategy. 
870.  🔴 Production DNS/domain setup. 
871.  🔴 Production TLS certificate. 
872.  🔴 Production CSP final tuning. 
873.  🔴 Production cookie/domain validation. 
874.  🔴 Production WebSocket origin validation. 
875.  🔴 Production OAuth origin validation. 
876.  🔴 Production Stripe webhook endpoint setup. 
877.  🔴 Production connected-account webhook validation. 
878.  🔴 Production Google API restrictions. 
879.  🔴 Production browser-key restrictions. 
880.  🔴 Production SMTP sender verification. 
881.  🔴 Production backup activation. 
882.  🔴 Production monitoring activation. 
883.  🔴 Production alert routing. 
884.  🔴 Production admin MFA activation. 
885.  🔴 Production ruleset/branch protection. 
886.  🔴 Real low-value money test. 
887.  🔴 Real low-value payout test. 
888.  🔴 Real refund test. 
889.  🔴 Real cancellation test. 
890.  🔴 Real dispute/admin settlement test. 
891.  🔴 Reconciliation after real payment test. 
892.  🔴 Accounting verification real transaction. 
893.  🔴 Final launch checklist. 
894.  🔴 Final database backup immediately before launch. 
895.  🔴 Final secrets audit. 
896.  🔴 Final dependency audit. 
897.  🔴 Final vulnerability scan. 
898.  🔴 Final frontend production build. 
899.  🔴 Final Maven verify. 
900.  🔴 Final full runtime smoke. 
901.  🔴 Final Stripe E2E. 
902.  🔴 Final OAuth E2E. 
903.  🔴 Final email E2E. 
904.  🔴 Final location/privacy E2E. 
905.  🔴 Final admin E2E. 
906.  🔴 Final backup restore proof. 
907.  🔴 Final rollback proof. 
908.  🔴 Final finance reconciliation healthy. 
909.  🔴 Final security sign-off. 
910.  🔴 Final privacy sign-off. 
911.  🔴 Final financial sign-off. 
912.  🔴 Final operational sign-off. 
913.  🔴 Final legal/compliance sign-off. 
914.  🔴 Controlled closed beta. 
915.  🔴 Monitor beta errors. 
916.  🔴 Monitor payment failures. 
917.  🔴 Monitor disputes. 
918.  🔴 Monitor payout failures. 
919.  🔴 Monitor fraud. 
920.  🔴 Beta feedback fixes. 
921.  🔴 Second security review po beta. 
922.  🔴 Performance tuning po realnym ruchu. 
923.  🔴 DB index tuning po realnym ruchu. 
924.  🔴 UX tuning po beta. 
925.  🔴 Customer support tuning. 
926.  🔴 Stop/go launch decision. 
927.  🔴 Public launch. 
928.  🔴 Post-launch monitoring. 
929.  🔴 Post-launch incident drills. 
930.  🔴 Pierwszy production backup restore drill po launch. 
931.  🔴 Pierwszy monthly finance reconciliation review. 
932.  🔴 Pierwszy security patch cycle. 
933.  🔴 Pierwszy dependency-update cycle. 
934.  🔴 Pierwszy legal/compliance review po realnym użyciu. 
935.  🔴 Native-app planning po web MVP. 
936.  🔴 Advanced matching po zebraniu danych. 
937.  🔴 Push notification infrastructure. 
938.  🔴 Promo/referral system. 
939.  🔴 Structured shopping. 
940.  🔴 Expanded admin RBAC. 
941.  🔴 HA/multi-node deployment. 
942.  🔴 Shared distributed rate limiting. 
943.  🔴 External object storage. 
944.  🔴 Managed PostgreSQL lub HA DB. 
945.  🔴 CDN/static asset strategy. 
946.  🔴 Multi-region/DR dopiero przy skali. 
947.  🔴 Background job infrastructure jeśli skala tego wymusi. 
948.  🔴 Event/outbox architecture jeśli skala tego wymusi. 
949.  🔴 Notification service extraction jeśli będzie potrzebny. 
950.  🔴 Search/matching service extraction jeśli będzie potrzebny. 
951.  ✅ Nie rozbijać escrow/job core na mikroserwisy bez realnego powodu. 
952.  ✅ **Location-retention audit i techniczny purge exact execution data — ukończone.** 
953.  ✅ **Final financial crash-window audit — główne outbound/provider/DB boundaries domknięte przez #151, #153–#177 wraz z rollback/recovery proofs.** 
954.  🔴 **Następny finansowy blocker: real Stripe Connect E2E.** 
955.  🟡 **Backup + restore — DB/attachment restore drills działają; off-host/encryption/schedule/retention/production DR nadal otwarte.** 
956.  🔴 **Branch protection/ruleset — zweryfikowane 2026-09-02: `master protected=false`, required checks wyłączone, `rulesets=[]`.**
957.  🟡 **TERAZ: pełny authorization/privacy/IDOR audit — PR-y #178–#234 są zmerge’owane i zielone; ten pakiet domyka fail-closed identity boundary przed utworzeniem prywatnej wyceny i wywołaniem providera tras; audyt trwa na pozostałych prywatnych zasobach.**
958.  🔴 **Następnie: admin MFA.** 
959.  🟡 **Następnie: monitoring/alerting.** 
960.  🔴 **Następnie: disaster/release runbook.** 
961.  🔴 **Następnie: staging environment.** 
962.  🔴 **Następnie: pełne staging E2E.** 
963.  🔴 **Następnie: legal/compliance closure.** 
964.  🔴 **Następnie: controlled pilot.** 
965.  🔴 **Dopiero wtedy: prawdziwe customer funds na większą skalę.** 
