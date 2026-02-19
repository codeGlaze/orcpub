(ns orcpub.routes.folder
  (:require [datomic.api :as d]
            [orcpub.dnd.e5.folder :as folder]
            [orcpub.entity.strict :as se]))

(def pull-folder
  [:db/id ::folder/name {::folder/character-ids [:db/id ::se/owner ::se/summary]}])

(defn create-folder [{:keys [conn identity] folder-data :transit-params}]
  ;; Whitelist only ::folder/name from client data; owner is always server-set
  (let [username (:user identity)
        result @(d/transact conn [{::folder/name (::folder/name folder-data)
                                   ::folder/owner username}])
        new-id (-> result :tempids first val)]
    {:status 200 :body (d/pull (d/db conn) '[*] new-id)}))

(defn folders [{:keys [db identity]}]
  (let [username (:user identity)
        result (d/q [:find `(~'pull ~'?e ~pull-folder)
                     :in '$ '?username
                     :where ['?e ::folder/owner '?username]]
                    db username)
        mapped (map (fn [[f]]
                      (update f ::folder/character-ids
                              (fn [chars]
                                (map (fn [{:keys [:db/id ::se/owner ::se/summary]}]
                                       (assoc summary :db/id id ::se/owner owner))
                                     chars))))
                    result)]
    {:status 200 :body mapped}))

(defn update-folder-name [{:keys [conn]
                           folder-name :transit-params
                           {:keys [id]} :path-params}]
  @(d/transact conn [{:db/id id ::folder/name folder-name}])
  {:status 200 :body (d/pull (d/db conn) pull-folder id)})

(defn add-character [{:keys [db conn]
                      character-id :transit-params
                      {:keys [id]} :path-params}]
  ;; Enforce at-most-one-folder: retract from existing folder first
  (when-let [existing-id (d/q '[:find ?f .
                                 :in $ ?char
                                 :where [?f :orcpub.dnd.e5.folder/character-ids ?char]]
                               db character-id)]
    @(d/transact conn [[:db/retract existing-id ::folder/character-ids character-id]]))
  @(d/transact conn [{:db/id id ::folder/character-ids character-id}])
  {:status 200 :body (d/pull (d/db conn) '[*] id)})

(defn remove-character [{:keys [conn]
                         {:keys [id character-id]} :path-params}]
  @(d/transact conn [[:db/retract id ::folder/character-ids (Long/parseLong character-id)]])
  {:status 200 :body (d/pull (d/db conn) '[*] id)})

(defn delete-folder [{:keys [conn]
                      {:keys [id]} :path-params}]
  ;; retractEntity removes only the folder entity; referenced character entities are unaffected
  @(d/transact conn [[:db/retractEntity id]])
  {:status 200})
