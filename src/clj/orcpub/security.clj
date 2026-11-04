(ns orcpub.security
  "Abuse limits on the account forms: one policy table (auth-guards), one guard over it, the
   Turnstile check, and the cadence of the notices sent to account owners.
   See orcpub.time for date/time utilities."
  (:require [clj-http.client :as client]
            [clojure.string :as s]
            [orcpub.config :as config]
            [orcpub.time :as time :refer [minutes hours ago]]))

(defn- log-safe
  "`v` as text fit for one log line: control characters removed, at most 100 characters; nil stays nil."
  [v]
  (when (some? v)
    (let [t (clojure.string/replace (str v) #"\p{Cntrl}" "")]
      (subs t 0 (min 100 (count t))))))

(defn audit!
  "Writes one log line for security event `event` (a keyword, like :sign-in-failed) on `request`,
   naming account `username` when given: the player's address, the proxy it came through when that
   differs, and the forwarded chain; never a password, token or email address. Returns nil."
  ([event request] (audit! event request nil))
  ([event request username]
   (println (str "security: " (name event)
                 (when username (str " user=" (log-safe username)))
                 " ip=" (log-safe (:remote-addr request))
                 (when-let [p (:peer-addr request)]
                   (when (not= p (:remote-addr request)) (str " via=" (log-safe p))))
                 (when-let [f (get-in request [:headers "x-forwarded-for"])]
                   (str " fwd=" (log-safe f)))))
   nil))

;; ---------------------------------------------------------------------------
;; Windowed limits for the endpoints that cost something to serve: the two that
;; SEND MAIL spend our sending reputation on a message a stranger chose, not us.
;; Neither limit may change the response -- an endpoint that answers differently
;; once throttled is an oracle for whether a limit was reached, which on reset is the membership test already closed.
;; ---------------------------------------------------------------------------

;; A limit nobody counts is a limit nobody can tune: numbers like ten signups
;; an hour from one household are guesses, validated only by how often they bite.
;; Steady refusals across many hosts means the number is too low and hurting real
;; people; a sudden spike on one host means it's doing its job.
(def ^:private refusals (atom {}))

(defn note-refusal!
  "Records a refusal against a limit, and WHO it was. The count alone cannot
   tell one host refused twenty times from twenty hosts refused once each --
   the first is somebody hammering, the second is a spread, and only the second
   suggests a pool of addresses working through the same list."
  ([what] (note-refusal! what nil))
  ([what who]
   (swap! refusals update what
          (fn [{:keys [n who-set] :or {n 0 who-set #{}}}]
            {:n (inc n) :who-set (cond-> who-set who (conj who))}))
   nil))

(defn take-refusals! []
  (first (reset-vals! refusals {})))

(defn refusal-summary
  "A line for the log, or nil on an hour when nothing was turned away. Each
   limit reports how many refusals and across how many distinct sources, which
   is the difference between one bad actor and a pool of them."
  [counts]
  (when (seq counts)
    (str "limits: "
         (->> counts
              (sort-by (comp - :n val))
              (map (fn [[what {:keys [n who-set]}]]
                     (str (name what) " " n
                          (when (> (count who-set) 1)
                            (str " from " (count who-set) " sources")))))
              (interpose ", ")
              (apply str)))))

;; ---------------------------------------------------------------------------
;; Tokens withdrawn by a password change
;;
;; Holds the moment each account's password last moved; a token minted before
;; that is refused. IN MEMORY, not a database read, because check-auth runs on
;; every request doing pure signature verification, and only holds accounts whose password moved within the token lifetime -- older entries have already expired on their own.
;; ---------------------------------------------------------------------------

(def ^:private password-changes (atom {}))

(defn note-password-changed!
  "Records that `username`'s password moved, withdrawing every token minted
   before that instant. Defaults to now."
  ([username] (note-password-changed! username (System/currentTimeMillis)))
  ([username at-millis]
   (swap! password-changes assoc username at-millis)
   at-millis))

(defn token-withdrawn?
  "Whether a token minted at `minted-millis` has been withdrawn for `username`.

   A token carrying no minted stamp counts as older than any change: it predates
   this mechanism, and the only accounts in the map are ones whose password moved
   recently enough for the question to still matter."
  [username minted-millis]
  (boolean
   (when-let [changed-at (get @password-changes username)]
     (< (or minted-millis 0) changed-at))))

(defn forget-password-changes-before!
  "Drops entries older than `cutoff-millis`. Every token they could withdraw has
   expired by then, so keeping them only grows the map."
  [cutoff-millis]
  (swap! password-changes
         (fn [m] (into {} (remove (fn [[_ at]] (< at cutoff-millis))) m)))
  nil)

(defn absorb-password-changes!
  "Folds `username->millis`, read from the database, into the map, keeping the
   later instant per account, then drops entries older than `cutoff-millis`.
   GOTCHA: merges rather than replaces -- the database read is a snapshot, and
   a reset or sign-out-everywhere committing after the read would be dropped
   by a replace, reinstating the tokens it withdrew until the next hourly
   refresh. See account-flows.md."
  [username->millis cutoff-millis]
  (swap! password-changes
         (fn [m]
           (into {}
                 (remove (fn [[_ at]] (< at cutoff-millis)))
                 (merge-with max m (into {} username->millis)))))
  nil)

(defn restore-password-changes!
  "Repopulates the map, for boot.

   Without this a restart quietly reinstates every token the map was holding, and
   nothing would notice -- the failure is silent and looks exactly like working,
   which is why it has a test of its own."
  [username->millis]
  (reset! password-changes (into {} username->millis))
  nil)

(defn password-changes-snapshot
  "The map as it stands. For the boot summary and for tests."
  []
  @password-changes)

;; Every recorded event is [instant who]: `who` is what a distinct-count limit counts (the typed
;; name behind a connection, or the connection behind a typed name); nil when nothing needs it.
(def ^:private sightings (atom {}))

;; The longest window any limit uses; entries older than this belong to nobody.
(def ^:private prune-horizon (hours 24))

(defn- prune [m cutoff]
  (into {}
        (keep (fn [[k ts]]
                (let [live (filterv #(not (.isBefore ^java.time.Instant (first %) cutoff)) ts)]
                  (when (seq live) [k live]))))
        m))

(defn- recent
  "The sightings of `k` inside `window`, without recording anything."
  [k window]
  (let [cutoff (ago window)]
    (filterv #(not (.isBefore ^java.time.Instant (first %) cutoff)) (get @sightings k))))

(defn- sight!
  "Records one sighting of `k` by `who` and returns those inside `window` from BEFORE it. The
   record and the read are one swap, so simultaneous callers cannot both be told yes on the last
   slot."
  [k window who]
  (let [cutoff (ago window)
        at (time/now)
        [before _] (swap-vals! sightings
                               (fn [m]
                                 (-> (prune m (ago prune-horizon))
                                     (update k #(conj (filterv (fn [e] (not (.isBefore ^java.time.Instant (first e) cutoff))) %)
                                                      [at who])))))]
    (filterv #(not (.isBefore ^java.time.Instant (first %) cutoff)) (get before k))))

(defn under-limit?
  "True while `k` has been seen fewer than `limit` times inside `window`, and false once it has hit
   the limit. `k` is any value; a vector's first element names the limit in the logs. Used directly
   only by endpoints that are not account forms; those go through `guard`."
  [k limit window]
  (let [allowed (< (count (sight! k window nil)) limit)]
    (when-not allowed
      (note-refusal! (if (vector? k) (first k) k)
                     (when (vector? k) (second k))))
    allowed))

;; The probe has no login, so IP is the only handle on who is asking. A real
;; character builder fires it once per image field, well under this; a script
;; walking a URL list to keep the fetch pool occupied is what it stops.
(def image-probe-per-host-hourly 300)

(defn image-probe-allowed? [ip]
  (under-limit? [:image-probe-host ip] image-probe-per-host-hourly (hours 1)))

;; ---------------------------------------------------------------------------
;; The policy of every account endpoint a stranger can reach, as data. `guard` and `record!` below
;; are the only code that reads it; handlers hold no numbers. The route-table test fails when a
;; route is missing or stale, and the table-driven tests iterate it.
;; ---------------------------------------------------------------------------

(def auth-guards
  "Per route key: :limits [[scope n window] ...] (scope :per-address :per-host :per-name
   :names-per-host; window :minute :hour; a fourth element :when-failing refuses only the failing
   attempt that reaches n), :counted-on :failure (else every request counts), :spend
   :when-actionable (only when ctx's :actionable delay is true), :turnstile :always or
   :after-failure, :answer :uniform (the ordinary 200) :message (the :message) :error (the form's
   own), :notify {:kind :scope :distinct :window} (see record!); or {:none \"why\"}."
  {:orcpub.routes.auth/login
   {:limits [[:names-per-host 5 :minute] [:per-name 5 :minute :when-failing]]
    :counted-on :failure
    :turnstile :after-failure
    :answer :error
    :notify {:kind :sign-in-attempts :scope :per-name :distinct 3 :window :minute}}
   ;; What this defends is NOT the account table -- an unverified account cannot log in, so bulk
   ;; signups sit inert. What signup CAN do is send mail to an address the filler doesn't own.
   ;; Sized to real use (eight people at one game night, plus retries and typos).
   :orcpub.routes.auth/register
   {:limits [[:per-host 10 :hour]]
    :turnstile :always
    :answer :message
    :message (str "Too many accounts have been created from this connection in the last hour. "
                  "Try again a little later, or email us if you are stuck.")}
   :orcpub.routes.auth/verify
   {:none "the link carries an unguessable verification key; a wrong key changes nothing"}
   ;; Three mails an hour to one address leaves room for "it never arrived, send it again"; the
   ;; per-host limit stops somebody walking a list of addresses through the endpoint.
   :orcpub.routes.auth/re-verify
   {:limits [[:per-address 3 :hour] [:per-host 10 :hour]]
    :turnstile :always
    :answer :uniform
    :spend :when-actionable}
   :orcpub.routes.auth/send-password-reset
   {:limits [[:per-address 3 :hour] [:per-host 10 :hour]]
    :turnstile :always
    :answer :uniform
    :spend :when-actionable}
   :orcpub.routes.auth/reset-password
   {:none "needs the unguessable single-use reset key from the mailed link"}
   :orcpub.routes.auth/reset-password-page
   {:none "a page; needs the unguessable reset key and changes nothing"}
   :orcpub.routes.auth/check-username
   {:none "usernames are public handles (profiles, shares), so membership is not a secret"}
   :orcpub.routes.auth/request-email-change
   {:none "needs a signed-in session and the current password, and has its own cooldown (email-change-rate-limited?)"}
   :orcpub.routes/unsubscribe
   {:none "the link carries a signed token; an unsigned or tampered one is refused"}})

(defn policy
  "The guard policy of `route`, or nil for a route that has none (or is unknown)."
  [route]
  (let [p (get auth-guards route)]
    (when-not (:none p) p)))

(def ^:private windows {:minute (minutes 1) :hour (hours 1)})

(def ^:private scopes
  {:per-address {:subject :address}
   :per-host {:subject :ip}
   :per-name {:subject :typed-name :who :ip}
   :names-per-host {:subject :ip :who :typed-name :distinct? true}})

(defn limit-name
  "The name a limit goes by in the logs and the busiest line: the route plus the scope."
  [route scope]
  (keyword (str (name route) "-" (name scope))))

(defn- normalise
  "Typed text as the lookups see it: trimmed and lowercased. Anything else is left alone."
  [v]
  (if (string? v) (s/lower-case (s/trim v)) v))

(defn- limit-key [route scope ctx]
  [(limit-name route scope) (normalise (get ctx (:subject (scopes scope))))])

(defn- who-of [scope ctx]
  (normalise (some->> (:who (scopes scope)) (get ctx))))

(defn- full?
  "Whether `entries` (sightings of one limit) have reached `n`."
  [scope n entries]
  (>= (if (:distinct? (scopes scope)) (count (distinct (map second entries))) (count entries)) n))

;; ---------------------------------------------------------------------------
;; Cloudflare Turnstile (managed mode), on the forms whose policy asks for it.
;; GOTCHA: fails OPEN. If Cloudflare cannot be reached the request goes through; only an
;; answer of success=false (or no token at all) refuses. The secret key is never logged.
;; ---------------------------------------------------------------------------

(def ^:private turnstile-endpoint "https://challenges.cloudflare.com/turnstile/v0/siteverify")

;; Short enough that a stalled third party cannot hold a signup open (same idea as orcpub.pwned).
(def ^:private turnstile-timeout-ms 4000)

(defn turnstile-enabled?
  "True only when both TURNSTILE_SITE_KEY and TURNSTILE_SECRET_KEY are set."
  []
  (config/turnstile-enabled?))

(defn- post-siteverify
  "POSTs to Cloudflare; returns the parsed JSON body as a map. Throws on transport failure,
   non-2xx or an unparseable body. A separate fn so tests can with-redefs it."
  [token remote-ip]
  (let [{:keys [status body]}
        (client/post turnstile-endpoint
                     {:form-params (cond-> {:secret (config/turnstile-secret-key)
                                            :response token}
                                     (not (s/blank? remote-ip)) (assoc :remoteip remote-ip))
                      :as :json
                      :socket-timeout turnstile-timeout-ms
                      :connection-timeout turnstile-timeout-ms
                      :throw-exceptions false
                      :cookie-policy :none
                      :redirect-strategy :none})]
    (if (<= 200 status 299)
      body
      (throw (ex-info "siteverify non-2xx" {:status status})))))

;; A sustained outage would otherwise log a line per signup.
(def ^:private turnstile-log-window-ms 60000)
(def ^:private turnstile-last-logged (atom 0))

(defn- turnstile-warn-unreachable! [reason]
  (let [now (System/currentTimeMillis)
        [before _] (swap-vals! turnstile-last-logged #(if (> (- now %) turnstile-log-window-ms) now %))]
    (when (> (- now before) turnstile-log-window-ms)
      ;; Reason is an exception class name or HTTP status: never the secret or the token.
      (println "turnstile: siteverify unreachable, letting signup through -" reason))))

(defn turnstile-verify
  "Checks a Turnstile `token` for `remote-ip`. Returns :pass, :fail, or :unreachable (the caller
   treats :unreachable as a pass). A blank token is :fail without calling Cloudflare."
  [token remote-ip]
  (if (s/blank? token)
    :fail
    (try
      (let [result (post-siteverify token remote-ip)]
        (if (true? (:success result)) :pass :fail))
      (catch Exception e
        (turnstile-warn-unreachable! (if-let [st (:status (ex-data e))]
                             (str "HTTP " st)
                             (.. e getClass getSimpleName)))
        :unreachable))))

;; ---------------------------------------------------------------------------
;; Sign-in challenge after a failure. A failed sign-in marks the NEXT attempt from that
;; connection with that typed name as needing the challenge. The key is the typed text, trimmed
;; and lowercased as the lookup does, never the account, so a mark exists for a name with no
;; account exactly as for one with an account and reveals nothing about either.
;; ---------------------------------------------------------------------------

(def login-challenge-ms (* 15 60 1000))

;; Bounds memory against a flood of invented names from many connections.
(def ^:private login-challenge-cap 50000)

(def ^:private login-challenges (atom {}))

(defn- login-challenge-key [typed-name remote-ip]
  [(normalise (str typed-name)) remote-ip])

(defn mark-login-challenge!
  "Marks `typed-name` from `remote-ip` as needing the challenge on its next attempt, for 15 minutes.
   Does nothing while Turnstile is off."
  [typed-name remote-ip]
  (when (turnstile-enabled?)
    (let [now (System/currentTimeMillis)]
      (swap! login-challenges
             (fn [m]
               (assoc (if (< login-challenge-cap (count m))
                        {}
                        (into {} (remove (fn [[_ until]] (< until now))) m))
                      (login-challenge-key typed-name remote-ip)
                      (+ now login-challenge-ms))))))
  nil)

(defn clear-login-challenge! [typed-name remote-ip]
  (swap! login-challenges dissoc (login-challenge-key typed-name remote-ip))
  nil)

(defn login-challenge-required?
  "True while Turnstile is on and `typed-name` from `remote-ip` failed a sign-in in the last 15 minutes."
  [typed-name remote-ip]
  (boolean (and (turnstile-enabled?)
                (when-let [until (get @login-challenges (login-challenge-key typed-name remote-ip))]
                  (> until (System/currentTimeMillis))))))

;; ---------------------------------------------------------------------------
;; The one guard. A handler calls `guard` before it does the work and, for a form that counts
;; failures, `record!` when one happens; both read auth-guards and nothing else.
;; ---------------------------------------------------------------------------

(defn- refuse! [k]
  (note-refusal! (first k) (second k))
  {:reason :limit :limit (first k)})

(defn- challenge-refusal
  "{:reason :turnstile :blank? ...} when the policy wants the challenge for this request, it is on,
   and the token fails it. :unreachable is not a refusal."
  [p {:keys [token ip typed-name]}]
  (when (and (:turnstile p)
             (turnstile-enabled?)
             (or (= :always (:turnstile p)) (login-challenge-required? typed-name ip))
             (= :fail (turnstile-verify token ip)))
    {:reason :turnstile :blank? (s/blank? token)}))

(defn- inspect-limits
  "The first limit already full, without counting this request: for forms that count failures."
  [route p ctx]
  (some (fn [[scope n window when-failing]]
          (let [k (limit-key route scope ctx)]
            (when (and (not when-failing) (full? scope n (recent k (windows window))))
              (refuse! k))))
        (:limits p)))

(defn- spend-limits
  "Counts this request against each limit in order, stopping at the first that is full. Skipped when
   the policy spends only on an actionable request and this one is not."
  [route p ctx]
  (when (or (not= :when-actionable (:spend p)) (force (:actionable ctx)))
    (some (fn [[scope n window]]
            (let [k (limit-key route scope ctx)]
              (when (full? scope n (sight! k (windows window) (who-of scope ctx)))
                (refuse! k))))
          (:limits p))))

(defn guard
  "nil when the request may go on, else a refusal: {:reason :turnstile :blank? bool} or
   {:reason :limit :limit kw}. `route` is a key of auth-guards; `ctx` holds what the handler knows:
   :ip, :address, :typed-name, :token, and :actionable (a delay: whether this request has anything
   to do, forced only after the challenge). A route with no policy is never refused. Order: limits
   that merely look (failure-counted forms), the challenge, then limits that count -- so a failed
   challenge spends no allowance and a refusal can never come from a lookup."
  [route ctx]
  (when-let [p (policy route)]
    (let [on-failure? (= :failure (:counted-on p))]
      (or (when on-failure?
            (when-let [r (inspect-limits route p ctx)]
              (when (= :after-failure (:turnstile p))
                (mark-login-challenge! (:typed-name ctx) (:ip ctx)))
              r))
          (challenge-refusal p ctx)
          (when-not on-failure? (spend-limits route p ctx))))))

(defn record!
  "Counts an outcome for a form whose policy counts failures. :failure records it against every
   limit, marks the next attempt for the challenge when the policy has one, and returns
   {:refusal r-or-nil :notify kind-or-nil}: a refusal when a :when-failing limit is now full, and the
   kind of notice to claim when the policy's :notify spread has been reached. :success clears the
   mark. Returns nil for a route with no policy."
  [route event ctx]
  (when-let [p (policy route)]
    (case event
      :success (do (when (= :after-failure (:turnstile p))
                     (clear-login-challenge! (:typed-name ctx) (:ip ctx)))
                   nil)
      :failure
      (let [seen (mapv (fn [[scope n window when-failing :as limit]]
                         (let [k (limit-key route scope ctx)]
                           [limit k (sight! k (windows window) (who-of scope ctx))]))
                       (:limits p))
            _ (when (= :after-failure (:turnstile p))
                (mark-login-challenge! (:typed-name ctx) (:ip ctx)))
            {:keys [kind scope distinct window]} (:notify p)]
        {:refusal (some (fn [[[scope n _ when-failing] k before]]
                          (when (and when-failing
                                     (full? scope n (conj before [nil (who-of scope ctx)])))
                            (refuse! k)))
                        seen)
         :notify (when (and kind
                            (>= (count (clojure.core/distinct
                                        (map second (recent (limit-key route scope ctx) (windows window)))))
                                distinct))
                   kind)}))))

;; ---------------------------------------------------------------------------
;; What the hourly heartbeat reports.
;; ---------------------------------------------------------------------------

(defn busiest
  "The heaviest single user of each limit in the window, whether or not it was
   ever refused. A host that stops at ten signups an hour, every hour, never
   trips anything and so never appears in the refusals at all -- this is the
   only place it shows up."
  []
  (->> @sightings
       (reduce (fn [acc [k ts]]
                 (let [limit (if (vector? k) (first k) k)]
                   (update acc limit (fnil max 0) (count ts))))
               {})))

(defn- caps
  "What each hourly limit allows, read off the policy table (plus the image probe)."
  []
  (into {:image-probe-host image-probe-per-host-hourly}
        (for [[route p] auth-guards
              [scope n window] (:limits p)
              :when (= :hour window)]
          [(limit-name route scope) n])))

(defn busiest-summary
  "A line naming any limit whose heaviest user is at least at `floor` of it --
   the quiet version of abuse, which sits just inside every number it is given."
  [highs floor]
  (let [caps (caps)
        near (for [[what n] highs
                   :let [cap (get caps what)]
                   :when (and cap (>= n (* floor cap)))]
               (str (name what) " " n "/" cap))]
    (when (seq near)
      (str "limits, busiest source: " (apply str (interpose ", " near))))))

(defn summary-job
  "Heartbeat job: at most two lines an hour, and silent when there is nothing
   to say. The first is what was turned away and how widely; the second is the
   heaviest user of any limit that is sitting at 80% or more of it."
  [_conn]
  (let [highs (busiest)]
    (when-let [line (refusal-summary (take-refusals!))]
      (println line))
    (when-let [line (busiest-summary highs 0.8)]
      (println line))))

;; ---------------------------------------------------------------------------
;; Notices to an account's owner about attempts on it: the first at once, then only when new
;; attempts came in, no sooner than :min-gap-hours after the last and at most :max-per-day in a
;; rolling day, each saying how many came in. Without a cadence the warning mail-bombs the owner.
;; ---------------------------------------------------------------------------

(def notices
  {:sign-in-attempts {:first :immediate :min-gap-hours 6 :max-per-day 4}
   :taken-signup {:first :immediate :min-gap-hours 6 :max-per-day 4}})

(def ^:private notice-state (atom {}))

(defn claim-notice!
  "Counts one attempt for `kind` (a key of `notices`) against `k` (the account or address) and says
   whether to mail the owner now: nil when not, {} for the first notice, {:since-last n} for a
   follow-up, n being the attempts since the previous notice, this one included."
  [kind k]
  (let [{:keys [min-gap-hours max-per-day]} (notices kind)
        at (time/now)
        day-ago (.minus ^java.time.Instant at (hours 24))
        gap-ago (.minus ^java.time.Instant at (hours min-gap-hours))
        entry-key [kind k]
        claimed (fn [m]
                  (let [{:keys [pending sent notified?] :or {pending 0 sent []}} (get m entry-key)
                        sent (filterv #(.isAfter ^java.time.Instant % day-ago) sent)
                        pending (inc pending)
                        due? (if (empty? sent)
                               (or notified? (= :immediate (:first (notices kind))))
                               (and (not (.isAfter ^java.time.Instant (peek sent) gap-ago))
                                    (< (count sent) max-per-day)))]
                    (assoc m entry-key
                           (if due?
                             {:pending 0 :sent (conj sent at) :seen at :notified? true
                              :claimed (if notified? {:since-last pending} {})}
                             {:pending pending :sent sent :seen at :notified? notified?}))))
        after (swap! notice-state
                     (fn [m]
                       (claimed (into {} (remove (fn [[_ e]] (.isBefore ^java.time.Instant (:seen e) day-ago))) m))))]
    (:claimed (get after entry-key))))
