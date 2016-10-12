(ns salava.social.db
 (:require [yesql.core :refer [defqueries]]
            [clojure.set :refer [rename-keys]]
            [clojure.java.jdbc :as jdbc]
            [salava.core.helper :refer [dump]]
            [slingshot.slingshot :refer :all]
            [salava.core.util :refer [get-db]]
            [salava.admin.helper :as ah]
            [salava.core.time :refer [unix-time get-date-from-today]]))

(defqueries "sql/social/queries.sql")

(defn messages-viewed
 "Save information about viewing messages."
 [ctx badge_content_id user_id]
  (try+
   (replace-badge-message-view! {:badge_content_id badge_content_id :user_id user_id} (get-db ctx))
   (catch Object _
     "error")))

(defn message! [ctx badge_content_id user_id message]
  (try+
   (insert-badge-message<! {:badge_content_id badge_content_id :user_id user_id :message message} (get-db ctx))
   "success"
   (catch Object _
     "error"
     )))

(defn get-badge-messages [ctx badge_content_id user_id]
  (let [badge-messages (select-badge-messages {:badge_content_id badge_content_id} (get-db ctx))]
    (do
      (messages-viewed ctx badge_content_id user_id)
      badge-messages
      )))

(defn get-badge-message-count [ctx badge_content_id user-id]
  (let [badge-messages-user-id-ctime (select-badge-messages-count {:badge_content_id badge_content_id} (get-db ctx))
        last-viewed (select-badge-message-last-view {:badge_content_id badge_content_id :user_id user-id} (into {:result-set-fn first :row-fn :mtime} (get-db ctx)))
        new-messages (if last-viewed (filter #(and (not= user-id (:user_id %)) (< last-viewed (:ctime %))) badge-messages-user-id-ctime) ())]
    {:new-messages (count new-messages)
     :all-messages (count badge-messages-user-id-ctime)}))


(defn get-badge-messages-limit [ctx badge_content_id page_count user_id]
  (let [limit 10
        offset (* limit page_count)
        badge-messages (select-badge-messages-limit {:badge_content_id badge_content_id :limit limit :offset offset} (get-db ctx))
        messages-left (- (:all-messages (get-badge-message-count ctx badge_content_id user_id)) (* limit (+ page_count 1)))]
    (if (= 0  page_count)
      (messages-viewed ctx badge_content_id user_id))
    {:messages badge-messages
     :messages_left (if (pos? messages-left) messages-left 0)}))


(defn message-owner? [ctx message_id user_id]
(let [message_owner (select-badge-message-owner {:message_id message_id} (into {:result-set-fn first :row-fn :user_id} (get-db ctx)))]
 (= user_id message_owner)))

(defn delete-message! [ctx message_id user_id]
  (try+
   (let [message-owner (message-owner? ctx message_id user_id)
         admin (ah/user-admin? ctx user_id) ]
     (if (or message-owner admin)
       (update-badge-message-deleted! {:message_id message_id} (get-db ctx))))
   "success"
   (catch Object _
     "error"
     )))

(defn is-connected? [ctx user_id badge_content_id]
  (let [id (select-connection-badge {:user_id user_id :badge_content_id badge_content_id} (into {:result-set-fn first :row-fn :badge_content_id} (get-db ctx)))]
    (= badge_content_id id)))

(defn create-connection-badge! [ctx user_id  badge_content_id]
  (try+
   (insert-connect-badge<! {:user_id user_id :badge_content_id badge_content_id} (get-db ctx))
   {:status "success" :connected? (is-connected? ctx user_id badge_content_id)}
   (catch Object _
     {:status "error" :connected? (is-connected? ctx user_id badge_content_id)}
     )))


(defn delete-connection-badge! [ctx user_id  badge_content_id]
  (try+
   (delete-connect-badge! {:user_id user_id :badge_content_id badge_content_id} (get-db ctx))
   "success"
   {:status "success" :connected? (is-connected? ctx user_id badge_content_id)}
   (catch Object _
     "error"
     {:status "error" :connected? (is-connected? ctx user_id badge_content_id)}
     )))


(defn get-connections-badge [ctx user_id]
  (select-user-connections-badge {:user_id user_id} (get-db ctx))
  )

(defn is-connected? [ctx user_id badge_content_id]
  (let [id (select-connection-badge {:user_id user_id :badge_content_id badge_content_id} (into {:result-set-fn first :row-fn :badge_content_id} (get-db ctx)))]
    (= badge_content_id id)))





