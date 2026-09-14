(ns orcpub.routes.party
  "HTTP route handlers for party management operations.

  Provides CRUD operations for D&D parties with error handling."
  (:require [clojure.spec.alpha :as spec]
            [datomic.api :as d]
            [orcpub.dnd.e5.party :as party]
            [orcpub.entity.strict :as se]
            [orcpub.errors :as errors]
            [orcpub.routes.share :as share]))

(defn- character?
  "Whether id names a character. A party holds only characters: an item, a folder or another party
   would load as a broken row."
  [db id]
  (boolean (and (integer? id) (::se/owner (d/pull db [::se/owner] id)))))

(defn- token-entry
  "The component entity remembering the share token a character was added with, or nil when the token is
   not that character's current one."
  [db character-id token]
  (when (share/token-current? db character-id token)
    {:orcpub.party-share/character character-id :orcpub.party-share/token token}))

(defn create-party
  "Creates a new party owned by the authenticated user.

  Args:
    request - HTTP request map with:
              :conn - Database connection
              :identity - Authenticated user identity
              :transit-params - Party data

  Returns:
    HTTP response with created party data

  Throws:
    ExceptionInfo on database failure with :party-creation-failed error code"
  [{:keys [db conn identity] party :transit-params}]
  (let [username (:user identity)
        ids      (::party/character-ids party)
        tokens   (keep (fn [{:keys [:orcpub.party-share/character :orcpub.party-share/token]}]
                         (when (contains? (set ids) character) (token-entry db character token)))
                       (::party/shared-tokens party))]
    (if-not (every? #(character? db %) ids)
      {:status 400 :body {:error :not-a-character}}
      (errors/with-db-error-handling :party-creation-failed
        {:username username}
        "Unable to create party. Please try again or contact support."
        (let [result @(d/transact conn [(cond-> (-> (select-keys party [::party/name ::party/character-ids])
                                                    (assoc ::party/owner username :db/id "new-party"))
                                          (seq tokens) (assoc ::party/shared-tokens tokens))])
              new-id (get-in result [:tempids "new-party"])]
          {:status 200 :body (d/pull (d/db conn) '[*] new-id)})))))

(def pull-party [:db/id ::party/name {::party/character-ids [:db/id ::se/owner ::se/summary]}
                 {::party/shared-tokens [:orcpub.party-share/character :orcpub.party-share/token]}])

(defn parties [{:keys [db identity]}]
  (let [username (:user identity)
        result (d/q [:find `(~'pull ~'?e ~pull-party)
                      :in '$ '?username
                      :where ['?e ::party/owner '?username]]
                    db
                    username)
        mapped (map
                (fn [[party]]
                  ;; Only tokens that still work: a revoked or pruned share leaves the row without homebrew.
                  (let [tokens (into {}
                                     (keep (fn [{:keys [:orcpub.party-share/character :orcpub.party-share/token]}]
                                             (when (share/token-current? db character token) [character token])))
                                     (::party/shared-tokens party))]
                    (-> party
                        (dissoc ::party/shared-tokens)
                        (update
                         ::party/character-ids
                         (fn [chars]
                           (map
                            (fn [{:keys [:db/id ::se/owner ::se/summary]}]
                              (cond-> (assoc summary
                                             :db/id id
                                             ::se/owner owner)
                                (tokens id) (assoc :orcpub.party-share/token (tokens id))))
                            chars))))))
                result)]
    {:status 200
     :body mapped}))

(defn update-party-name
  "Updates a party's name.

  Args:
    request - HTTP request with party name and party ID

  Returns:
    HTTP response with updated party data

  Throws:
    ExceptionInfo on database failure with :party-update-failed error code"
  [{:keys [db conn identity]
    party-name :transit-params
    {:keys [id]} :path-params}]
  (errors/with-db-error-handling :party-update-failed
    {:party-id id}
    "Unable to update party name. Please try again or contact support."
    @(d/transact conn [{:db/id id
                        ::party/name party-name}])
    {:status 200
     :body (d/pull (d/db conn) pull-party id)}))

(defn add-character
  "Adds a character to a party. The body is the character id, or {:character-id id :share-token token}
   when the character was opened from a share link; a current token is kept with the entry so the party
   page can load that character's homebrew. Anything that is not a character is refused."
  [{:keys [db conn identity]
    params :transit-params
    {:keys [id]} :path-params}]
  (let [[character-id token] (if (map? params) [(:character-id params) (:share-token params)] [params nil])
        previous (d/q '[:find [?t ...] :in $ ?party ?c
                        :where [?party ::party/shared-tokens ?t] [?t :orcpub.party-share/character ?c]]
                      db id character-id)
        entry    (token-entry db character-id token)]
    (if-not (character? db character-id)
      {:status 400 :body {:error :not-a-character}}
      (try
        @(d/transact conn (concat (when entry (map (fn [t] [:db/retractEntity t]) previous))
                                  [(cond-> {:db/id id ::party/character-ids character-id}
                                     entry (assoc ::party/shared-tokens [entry]))]))
        {:status 200 :body (d/pull (d/db conn) '[*] id)}
    (catch Exception e
      (println "ERROR: Failed to add character" character-id "to party" id ":" (.getMessage e))
      (throw (ex-info "Unable to add character to party. Please try again or contact support."
                      {:error :party-add-character-failed
                       :party-id id
                       :character-id character-id}
                      e)))))))

(defn remove-character
  "Removes a character from a party.

  Args:
    request - HTTP request with party ID and character ID

  Returns:
    HTTP response with updated party data

  Throws:
    ExceptionInfo on invalid character ID or database failure"
  [{:keys [db conn identity]
    {:keys [id character-id]} :path-params}]
  (let [char-id (errors/with-validation :invalid-character-id
                  {:character-id character-id}
                  "Invalid character ID format"
                  (Long/parseLong character-id))]
    (errors/with-db-error-handling :party-remove-character-failed
      {:party-id id :character-id char-id}
      "Unable to remove character from party. Please try again or contact support."
      @(d/transact conn (cons [:db/retract id ::party/character-ids char-id]
                              (map (fn [t] [:db/retractEntity t])
                                   (d/q '[:find [?t ...] :in $ ?party ?c
                                          :where [?party ::party/shared-tokens ?t] [?t :orcpub.party-share/character ?c]]
                                        db id char-id))))
      {:status 200 :body (d/pull db '[*] id)})))

(defn delete-party [{:keys [db conn identity]
                     {:keys [id]} :path-params}]
  (try
    @(d/transact conn [[:db/retractEntity id]])
    {:status 200}
    (catch Exception e
      (println "ERROR: Failed to delete party" id ":" (.getMessage e))
      (throw (ex-info "Unable to delete party. Please try again or contact support."
                      {:error :party-deletion-failed
                       :party-id id}
                      e)))))
