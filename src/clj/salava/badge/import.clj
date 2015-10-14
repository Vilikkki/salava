(ns salava.badge.import
  (:require [slingshot.slingshot :refer :all]
            [clj-http.client :as client]
            [salava.badge.main :as b]
            [salava.core.util :refer [map-sha256 file-from-url]]
            [salava.core.time :refer [unix-time]]
            [salava.core.helper :refer [dump]]
            [salava.user.main :as u]
            [salava.badge.assertion :as a]
            ))

(def api-root-url "https://backpack.openbadges.org/displayer")


(defn api-request
  ([method path] (api-request method path {}))
  ([method path post-params]
   (try+
     (:body
       (client/request
         {:method      method
          :url         (str api-root-url path)
          :as          :json
          :form-params post-params}))
     (catch [:status 404] {:keys [request-time headers body]}
       (if (= path "/convert/email")
         (throw+ "Backpack userId not found")
         (throw+ "Backpack data not found")))
     (catch Object _
       (throw+ "Error connecting to backpack")))))


(defn get-badge-type [badge]
  (if (= (get-in badge [:assertion :verify :type]) "hosted")
    "hosted"
    (if (and (= (get-in badge [:assertion :verify :type]) "signed")
             (not-empty (get-in badge [:imageUrl])))
      "signed"
      (if (:hostedUrl badge)
        "hostedUrl"))))


(defn get-assertion-and-key [type badge]
  "Get badge type and data.
  Return badge assertion and assertion key"
  (case type
    "hosted" [(:assertion badge) (:assertion badge)]
    "signed" [(a/fetch-signed-badge-assertion (:imageUrl badge)) (:imageUrl badge)]
    "hostedUrl" [(:hostedUrl badge) (:hostedUrl badge)]
    [{:error "Invalid assertion"} nil]))


(defn add-assertion-and-key [badge]
  (let [badge-type (get-badge-type badge)
        [assertion assertion-key] (get-assertion-and-key badge-type badge)
        old-assertion (:assertion badge)]
    (assoc badge :assertion (a/create-assertion assertion old-assertion)
                 :assertion_key assertion-key)))


(defn collect-badges
  "Collect badges fetched from groups"
  [badge-colls]
  (let [badges (flatten badge-colls)]
    (map add-assertion-and-key badges)))


(defn fetch-badges-by-group
  "Get badges from public group in Backpack"
  [email backpack-id group]
  (let [response (api-request :get (str "/" backpack-id "/group/" (:id group)))
        badges (:badges response)]
    (->> badges
         (map #(assoc % :_group_name (:name group)
                        :_email email)))))


(defn fetch-badges-from-groups
  "Fetch and collect users badges in public groups."
  [email backpack-id]
  (let [response (api-request :get (str "/" backpack-id "/groups"))
        groups (map #(hash-map :id (:groupId %)
                               :name (:name %))
                    (:groups response))]
    (if (pos? (count groups))
      (collect-badges (map #(fetch-badges-by-group email backpack-id %) groups)))))


(defn fetch-badges-from-backpack
  "Get badges by backpack email address"
  [email]
  (let [response (api-request :post "/convert/email" {:email email})]
    (fetch-badges-from-groups email (:userId response))))


(defn fetch-all-user-badges [backpack-emails]
  (if (empty? backpack-emails)
    (throw+ "User does not have any email addresses"))
  (loop [badges []
         emails backpack-emails]
    (if (empty? emails)
      badges
      (recur (concat badges (fetch-badges-from-backpack (first emails)))
             (rest emails)))))


(defn badge-to-import [ctx user-id badge]
  (let [expires (Integer. (re-find #"\d+" (get-in badge [:assertion :expires])))
        expired? (and (not= expires 0)
                      (< expires (unix-time)))
        exists? false                                            ;(b/user-owns-badge? ctx (:assertion badge) user-id)
        error (get-in badge [:assertion :error])]
    {:status (if (or expired? exists? error)
               "invalid"
               "ok")
     :message (cond
                exists? "You own this badge already"
                expired? "Badge is expired"
                error error
                :else "Save this badge")
     :badge {:name        (get-in badge [:assertion :badge :name])
             :description (get-in badge [:assertion :badge :description])
             :image_file  (get-in badge [:assertion :badge :image])}
     :key (map-sha256 (get-in badge [:assertion_key]))}))


(defn badges-to-import [ctx user-id]
  (try+
    (let [backpack-emails (u/user-backpack-emails ctx user-id)
          badges (fetch-all-user-badges backpack-emails)]
      {:status "success"
       :badges (map #(badge-to-import ctx user-id %) badges)
       :message ""})
    (catch Object _
      {:status "error"
       :badges []
       :message _})))

(defn save-badge-data! [ctx user-id badge]
  (try+
    (let [badge-id (b/save-badge-from-assertion! ctx badge user-id)
          tags (list (:_group_name badge))]
      (if (and tags
               (pos? badge-id))
        (b/save-badge-tags! ctx tags badge-id))
      {:id badge-id})
    (catch Object _
      {:id nil})))

(defn do-import [ctx user-id keys]
  (try+
    (let [backpack-emails (u/user-backpack-emails ctx user-id)
          all-badges (fetch-all-user-badges backpack-emails)
          badges-with-keys (map #(assoc % :key
                                          (map-sha256 (get-in % [:assertion_key])))
                                all-badges)
          badges-to-save (filter (fn [b]
                                   (some #(= (:key b) %) keys)) badges-with-keys)
          saved-badges (for [b badges-to-save]
                         (save-badge-data! ctx user-id b))]
      {:status "success"
       :message "Badges saved"
       :saved-count (->> saved-badges
                         (filter #(nil? (:id %)))
                         count)
       :error-count (->> saved-badges
                         (filter #(:id %))
                         count)})
    (catch Object _
      {:status "error" :message _})))