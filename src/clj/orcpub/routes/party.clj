(ns orcpub.routes.party
  "HTTP route handlers for party management operations.

  Provides CRUD operations for D&D parties with error handling."
  (:require [clojure.spec.alpha :as spec]
            [datomic.api :as d]
            [orcpub.dnd.e5.party :as party]
            [orcpub.entity.strict :as se]
            [orcpub.errors :as errors]))

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
  (let [username (:user identity)]
    (errors/with-db-error-handling :party-creation-failed
      {:username username}
      "Unable to create party. Please try again or contact support."
      (let [result @(d/transact conn [(assoc party ::party/owner username)])
            new-id (-> result :tempids first val)]
        {:status 200 :body (d/pull (d/db conn) '[*] new-id)}))))

(def pull-party [:db/id ::party/name {::party/character-ids [:db/id ::se/owner ::se/summary]}])

(defn parties [{:keys [db identity]}]
  (let [username (:user identity)
        result (d/q [:find `(~'pull ~'?e ~pull-party)
                      :in '$ '?username
                      :where ['?e ::party/owner '?username]]
                    db
                    username)
        mapped (map
                (fn [[party]]
                  (update
                   party
                   ::party/character-ids
                   (fn [chars]
                     (map
                      (fn [{:keys [:db/id ::se/owner ::se/summary]}]
                        (assoc summary
                               :db/id id
                               ::se/owner owner))
                      chars))))
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

(defn add-character [{:keys [db conn identity]
                      character-id :transit-params
                      {:keys [id]} :path-params}]
  (try
    @(d/transact conn [{:db/id id
                        ::party/character-ids character-id}])
    {:status 200 :body (d/pull db '[*] id)}
    (catch Exception e
      (println "ERROR: Failed to add character" character-id "to party" id ":" (.getMessage e))
      (throw (ex-info "Unable to add character to party. Please try again or contact support."
                      {:error :party-add-character-failed
                       :party-id id
                       :character-id character-id}
                      e)))))

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
      @(d/transact conn [[:db/retract id ::party/character-ids char-id]])
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
