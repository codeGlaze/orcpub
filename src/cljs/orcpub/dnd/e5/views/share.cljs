(ns orcpub.dnd.e5.views.share
  "The share controls on a character page: the sharing status line, the Share and Copy link
   buttons, and the short-link actions (new link, stop sharing). Every shared link passes through
   the integrations/share-url hook."
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [re-frame.db]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.event-utils :as event-utils]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.share-bundle :as sb]
            [orcpub.dnd.e5.share-url :as share-url]
            [orcpub.fork.branding :as branding]
            [orcpub.fork.integrations :as integrations]
            [orcpub.route-map :as routes]))

;; ─── Sharing ─────────────────────────────────────────────────
;; The primary action is "copy the link"; where navigator.share exists, "Share" also
;; hands the URL to the OS share sheet. Links pass through integrations/share-url.

(defn- char-url
  "Absolute, shareable URL for a character page (dynamic protocol/host/port
   so it works on localhost, LAN, and prod without hardcoding a domain), after the fork's
   share-url hook, with any fragment the hook added dropped."
  [id]
  (sb/share-base
   (integrations/share-url
    (str js/window.location.protocol "//"
         js/window.location.hostname
         (when-let [p js/window.location.port] (when (seq p) (str ":" p)))
         (routes/path-for routes/dnd-e5-char-page-route :id id)))))

(defn- exec-copy-fallback!
  "Clipboard copy for non-secure contexts where navigator.clipboard is
   absent (plain http). Returns true on success."
  [text]
  (let [ta (js/document.createElement "textarea")]
    (set! (.-value ta) text)
    (set! (.. ta -style -position) "fixed")
    (set! (.. ta -style -opacity) "0")
    (js/document.body.appendChild ta)
    (.select ta)
    (let [ok (try (js/document.execCommand "copy") (catch :default _ false))]
      (js/document.body.removeChild ta)
      ok)))

(defn- copy-to-clipboard!
  "Copy text, then call (on-done success?). Prefers the async Clipboard API,
   falls back to execCommand where it isn't available."
  [text on-done]
  (if (some-> js/navigator .-clipboard)
    (-> (.writeText js/navigator.clipboard text)
        (.then #(on-done true))
        (.catch #(on-done (exec-copy-fallback! text))))
    (on-done (exec-copy-fallback! text))))

(defn- copy-when-ready!
  "Copy the link a promise resolves to, from inside the click that started it, then call (on-done ok?).
   Safari only finishes a clipboard write begun during the click, so where the browser has ClipboardItem
   the pending link goes in as one; elsewhere the text is written when it arrives."
  [link-promise on-done]
  (if (and (exists? js/ClipboardItem) (some-> js/navigator .-clipboard .-write))
    (-> (.write js/navigator.clipboard
                #js [(js/ClipboardItem.
                      #js {"text/plain" (.then link-promise
                                               (fn [link]
                                                 (if link
                                                   (js/Blob. #js [link] #js {:type "text/plain"})
                                                   (throw (js/Error. "no link to copy")))))})])
        (.then #(on-done true))
        (.catch #(on-done false)))
    (-> link-promise
        (.then (fn [link] (if link (copy-to-clipboard! link on-done) (on-done false))))
        (.catch #(on-done false)))))

(defn- native-share?
  "True when the browser exposes the OS share sheet."
  []
  (boolean (some-> js/navigator .-share)))

(defn- share-token-url
  "The server address that makes, renews or ends the share token of character `id`. Returns a string."
  [id]
  (event-utils/url-for-route routes/dnd-e5-char-share-token-route :id id))

(defn- with-login
  "fetch options carrying the login token."
  [opts]
  (clj->js (update opts :headers merge (event-utils/auth-headers @re-frame.db/app-db))))

(defn- send-homebrew!
  "Promise of the token once the character's homebrew is on the server under it, or nil. resp is the
   token route's response, which carries the token and the server's caps."
  [id bundle resp]
  (-> (js/Promise.all #js [(.text resp) (share-url/encode-share bundle (share-url/share-caps-from resp))])
      (.then (fn [got]
               (let [token                 (aget got 0)
                     {:keys [bytes error]} (aget got 1)]
                 (when-not error
                   (-> (js/fetch (event-utils/url-for-route routes/dnd-e5-char-share-route :id id :token token)
                                 (with-login {:method  "PUT"
                                              :headers {"Content-Type" "application/octet-stream"}
                                              :body    bytes}))
                       (.then #(when (.-ok %) token)))))))))

(defn- refresh-share!
  "For a character its owner has shared, send the current homebrew so the link shows it. Promise of
   {:token t}, {:unshared true} when the character is not shared, {:unshared true :expired-on iso} when
   its last link expired unused and the owner has not acted on that, or nil when a request fails. Never
   creates a share: only Share link does."
  [id bundle]
  (-> (js/fetch (share-token-url id) (with-login {}))
      (.then (fn [resp]
               (cond
                 (.-ok resp)             (-> (send-homebrew! id bundle resp) (.then #(when % {:token %})))
                 (= 404 (.-status resp)) {:unshared true}
                 (= 410 (.-status resp)) (-> (.text resp) (.then (fn [on] {:unshared true :expired-on on})))
                 :else                   nil)))
      (.catch (fn [_] nil))))

(defn- start-share!
  "Share link: make the character's share and send its homebrew. Promise of the token, or nil."
  [id bundle]
  (-> (js/fetch (share-token-url id) (with-login {:method "PUT"}))
      (.then (fn [resp] (when (.-ok resp) (send-homebrew! id bundle resp))))
      (.catch (fn [_] nil))))

(defn- embedded-link
  "Promise of the share state for a link that carries the homebrew itself."
  [base container]
  (-> (share-url/build-share-payload container)
      (.then (fn [{:keys [tier payload]}]
               {:tier        tier
                :short-link? false
                :url         (if payload (str base "#c=" payload) base)}))))

(defn- new-link!
  "Asks the server for a new share token, which deletes the character's shared homebrew, so every link
   made before loads nothing. Promise of true when it worked."
  [id]
  (-> (js/fetch (share-token-url id) (with-login {:method "POST"}))
      (.then #(.-ok %))
      (.catch (fn [_] false))))

(defn- stop-sharing!
  "Asks the server to delete the character's share, or the note that its last link expired. Promise of
   true when it worked."
  [id]
  (-> (js/fetch (share-token-url id) (with-login {:method "DELETE"}))
      (.then #(.-ok %))
      (.catch (fn [_] false))))

(defn- reader-date
  "An ISO instant as a date in the reader's locale, e.g. March 3, 2027."
  [iso]
  (.toLocaleDateString (js/Date. iso) js/undefined #js {:year "numeric" :month "long" :day "numeric"}))

(defn share-controls
  "Reactive share cluster for a character: Copy link (plus native Share where
   supported), carrying a link with the character's homebrew embedded in the URL.
   `mode` :line draws the owner's status pill and growing actions; :row draws the
   list row's plain Copy link button.
   GOTCHA: recomputed only via an identical? check on character/plugins, so a
   click has the link ready synchronously for the native-share gesture. See share-links.md."
  [id mode]
  (let [state (r/atom {:tier :plain :url nil :copied? false})
        prev  (atom {})]
    (fn [id mode]
      ;; The character this button shares, by id. [:character] is the builder's working copy, which on the
      ;; character page and in the character list is some other character or none.
      (let [character @(subscribe [::char5e/character id])
            plugins   @(subscribe [:plugins])
            username  @(subscribe [:username])
            char-name @(subscribe [::char5e/character-name id])
            base      (char-url id)]
        ;; Recompute the link only when its inputs change (identical? = O(1)).
        (when (or (not (identical? character (:character @prev)))
                  (not (identical? plugins (:plugins @prev)))
                  (not= username (:username @prev)))
          (reset! prev {:character character :plugins plugins :username username})
          ;; Custom items stay out of the link: the server sends a character's equipped items with
          ;; the character to whoever may read it. Homebrew rides here because the server never had it.
          (let [plugins-bundle (sb/extract-bundle character plugins)
                container {:plugins plugins-bundle}]
            (if (empty? plugins-bundle)
              (swap! state assoc :tier :plain :url base :short-link? false)
              (do
                (swap! state assoc :tier :working :url base)
                ;; An owner who has shared this character gets its short link, kept current. One who has not
                ;; gets Share link, and nothing is stored until it is pressed. Anyone else, or a failed
                ;; request, embeds the bundle in the link as before.
                (-> (if (and username (= username (::entity/owner character)))
                      (refresh-share! id plugins-bundle)
                      (js/Promise.resolve nil))
                    (.then (fn [{:keys [token unshared expired-on]}]
                             (cond
                               token    {:tier :full :url (str base "#s=" token) :short-link? true :expired-on nil}
                               unshared {:tier :unshared :url base :short-link? false :expired-on expired-on}
                               :else    (embedded-link base container))))
                    (.then #(swap! state merge %)))))))
        (let [{:keys [tier url copied? short-link? expired-on]} @state
              url  (or url base)
              working? (= tier :working)
              owner? (and username (= username (::entity/owner character)))
              ;; Size caveats, shown once on a successful action. The full content
              ;; always rides along — we never strip it — so notices are only about
              ;; link length / transport, never about lost data.
              note (fn []
                     (case tier
                       :long (dispatch [:show-message
                                        "Link copied. It's a long link — some apps (Discord, SMS) can cut it off; if the recipient sees missing content, send them the .orcbrew file instead."])
                       :file (dispatch [:show-message
                                        "This character has too much custom content to fit in a link. A plain link was copied — share the .orcbrew file so the recipient gets the homebrew."])
                       nil))
              ;; A text button on the line; `tone` is :danger or :quiet.
              action (fn [icon label title on-click & [tone]]
                       [:button.share-action
                        {:type "button" :title title :disabled working? :on-click on-click
                         :class (when tone (str "share-action-" (name tone)))}
                        [:i.fa {:class icon}]
                        [:span label]])
              ;; Makes the share and settles the state on its link. Promise of the link, or nil.
              start-link (fn []
                           (swap! state assoc :tier :working)
                           (let [bundle (sb/extract-bundle character plugins)]
                             (-> (start-share! id bundle)
                                 (.then (fn [token]
                                          (if token
                                            (let [link (str base "#s=" token)]
                                              (swap! state assoc :tier :full :url link :short-link? true
                                                     :expired-on nil)
                                              link)
                                            (-> (embedded-link base {:plugins bundle})
                                                (.then (fn [s] (swap! state merge s) (:url s))))))))))
              ;; After a copy; `made?` when that press also made the share, as the list row's button does.
              copied (fn [ok? made?]
                       (if ok?
                         (do (swap! state assoc :copied? true)
                             (if made?
                               (dispatch [:show-message "Link copied. This character is now shared; its page has New link and Stop sharing."])
                               (note))
                             (js/setTimeout #(swap! state assoc :copied? false) 1800))
                         (dispatch [:show-message "The link could not be copied. Try Copy link again."])))
              share-link (action "fa-link" "Share link"
                                 "Make a short link to this character, custom content included."
                                 (fn [_] (start-link)))
              copy-link (action (cond copied? "fa-check" working? "fa-spinner" :else "fa-link")
                                (cond copied? "Copied!" working? "Preparing…" :else "Copy link")
                                "Copy a link to this character (custom content included)"
                                (fn [_] (copy-to-clipboard! url #(copied % false))))
              native (when (native-share?)
                       (action "fa-share-alt" "Share"
                               "Share this character (custom content included)"
                               (fn [_]
                                 (-> (.share js/navigator
                                             #js {:title (str (or char-name "D&D character") " — " branding/app-name)
                                                  :url url})
                                     (.then (fn [_] (note)))
                                     ;; user-cancelled / permission rejections are expected — swallow.
                                     (.catch (fn [_] nil))))))]
          (if (= mode :row)
            ;; The character list has no room for the line: one button that copies the link, making the share
            ;; first when there is none. The status, New link and Stop sharing stay on the character page.
            [:button.form-button.m-r-5
             {:type "button" :disabled working?
              :title "Copy a link to this character (custom content included)"
              :on-click (fn [_]
                          (if (= tier :unshared)
                            (copy-when-ready! (start-link) #(copied % (:short-link? @state)))
                            (copy-to-clipboard! url #(copied % false))))}
             (cond copied? "Copied!" working? "Preparing…" :else "Copy link")]
          [:div.share-line
           (cond
             ;; Shown on every visit until the owner shares again or dismisses it, so it cannot be missed once.
             (and (= tier :unshared) expired-on)
             [:<>
              [:span.share-pill.share-pill-expired {:title "It went unused, so it stopped working."}
               (str "Link expired " (reader-date expired-on))]
              share-link
              (action "fa-times" "Dismiss" "Hide this note. Share link makes a new link."
                      (fn [_]
                        (-> (stop-sharing! id)
                            (.then #(when % (swap! state assoc :expired-on nil)))))
                      :quiet)]

             (= tier :unshared)
             [:<>
              [:span.share-pill.share-pill-neutral "Not shared"]
              share-link]

             ;; Only a short link can be revoked; an embedded link carries its custom content itself.
             (and owner? short-link?)
             [:<>
              [:span.share-pill.share-pill-shared "Shared"]
              copy-link
              (action "fa-sync-alt" "New link"
                      "Make a new link. Links you shared before stop showing this character's custom content."
                      (fn [_]
                        (when (js/confirm "Links you shared before will stop showing this character's custom content. Make a new link?")
                          (swap! state assoc :tier :working)
                          (-> (new-link! id)
                              (.then (fn [ok?]
                                       ;; Forget the last inputs, so the content is sent again under the new token.
                                       (reset! prev {})
                                       (swap! state assoc :tier :plain :short-link? false)
                                       (dispatch [:show-message
                                                  (if ok?
                                                    "New link made. Links you shared before no longer show this character's custom content."
                                                    "Could not make a new link. Try again.")])))))))
              (action "fa-ban" "Stop sharing"
                      "Stop sharing this character. Links you shared before stop showing its custom content."
                      (fn [_]
                        (when (js/confirm "Links you shared before will stop showing this character's custom content. Stop sharing?")
                          (swap! state assoc :tier :working)
                          (-> (stop-sharing! id)
                              (.then (fn [ok?]
                                       ;; Nothing is sent again until Share link: the page now asks and gets a 404.
                                       (swap! state assoc :tier (if ok? :unshared :full) :short-link? (not ok?)
                                              :expired-on nil)
                                       (dispatch [:show-message
                                                  (if ok?
                                                    "Stopped sharing. Links you shared before no longer show this character's custom content."
                                                    "Could not stop sharing. Try again.")]))))))
                      :danger)
              native]

             :else
             [:<> copy-link native])]))))))

(defn share-line
  "A character's share line, under its page title."
  [id]
  [share-controls id :line])

(defn share-copy-button
  "The character list row's Copy link button, which makes the share first when there is none."
  [id]
  [share-controls id :row])
