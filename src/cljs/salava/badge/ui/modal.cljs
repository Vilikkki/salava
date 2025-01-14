(ns salava.badge.ui.modal
  (:require [reagent.core :refer [atom cursor]]
            [reagent.session :as session]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.core.i18n :refer [t]]
            [salava.badge.ui.helper :as bh]
            [salava.badge.ui.assertion :as a]
            [salava.badge.ui.settings :as se]
            [salava.core.ui.share :as s]
            [salava.core.ui.helper :refer [path-for private? plugin-fun hyperlink url? plugin-fun]]
            [salava.core.time :refer [date-from-unix-time]]
            [salava.social.ui.follow :refer [follow-badge]]
            [salava.core.ui.error :as err]
            [salava.core.ui.modal :as mo]
            [salava.core.ui.content-language :refer [init-content-language content-language-selector content-setter]]
            [salava.badge.ui.endorsement :as endr]
            [salava.badge.ui.issuer :as issuer]
            [salava.social.ui.badge-message-modal :refer [badge-message-link]]
            [salava.admin.ui.reporttool :refer [reporttool1]]
            [salava.badge.ui.verify :refer [check-badge]]
            [salava.core.ui.tag :as tag]
            [clojure.string :refer [blank? starts-with? split]]
            [salava.badge.ui.evidence :refer [evidence-icon]]
            [salava.core.helper :refer [dump]]
            [salava.badge.ui.block :as block]))


(defn init-badge-connection [state badge-id]
  (ajax/GET
    (path-for (str "/obpv1/social/connected/" badge-id))
    {:handler (fn [data]
                (swap! state assoc :receive-notifications data))}))

(defn init-data
  ([state id tab-no]
   (ajax/GET
     (path-for (str "/obpv1/badge/info/" id))
     {:handler (fn [data]
                 (do (reset! state (assoc data :id id
                                     :show-link-or-embed-code nil
                                     :show_evidence_options false
                                     :initializing false
                                     :content-language (init-content-language (:content data))
                                     :tab-no tab-no
                                     :permission "success"
                                     :evidence {:url nil}))

                   (if (:user-logged-in @state)  (init-badge-connection state (:badge_id data)))))}
     (fn [] (swap! state assoc :permission "error"))))

  ([state id tab-no data]
   (do (reset! state (assoc data :id id
                       :show-link-or-embed-code nil
                       :initializing false
                       :content-language (init-content-language (:content data))
                       :tab-no tab-no
                       :permission "success"))
     (if (:user-logged-in @state)  (init-badge-connection state (:badge_id data))))))


(defn show-settings-dialog [badge-id state init-data context]
  (ajax/GET
    (path-for (str "/obpv1/badge/settings/" badge-id) true)
    {:handler (fn [data]
                (swap! state assoc :badge-settings data (assoc data :new-tag ""))
                (if (= context "settings")
                  (swap! state assoc :tab [se/settings-tab-content data state init-data]
                         :tab-no 2
                         :evidences (:evidences data))
                  (swap! state assoc :tab [se/share-tab-content data state init-data]
                         :tab-no 3)))}))

(defn congratulate [state]
  (ajax/POST
    (path-for (str "/obpv1/badge/congratulate/" (:id @state)))
    {:handler (fn [] (swap! state assoc :congratulated? true))}))

(defn badge-endorsement-modal-link
  ([badge-id endorsement-count]
   (when (pos? endorsement-count)
     [:div.endorsement-link
      [:span [:i {:class "fa fa-handshake-o"}]]
      [:a {:href "#"
           :on-click #(do (.preventDefault %)
                        (mo/open-modal [:badge :endorsement] badge-id))}
       (if (== endorsement-count 1)
         (str  endorsement-count " " (t :badge/endorsement))
         (str  endorsement-count " " (t :badge/endorsements)))]]))
  ([params endorsement-count user-endorsement-count]
   (if-not (pos? user-endorsement-count)
     (badge-endorsement-modal-link (:badge-id params) endorsement-count)
     (let [endorsement-count (+ endorsement-count user-endorsement-count)]
       [:div.endorsement-link
        [:span [:i {:class "fa fa-handshake-o"}]]
        [:a {:href "#"
             :on-click #(do (.preventDefault %)
                          (mo/open-modal [:badge :endorsement] params))}
         (if (== endorsement-count 1)
           (str  endorsement-count " " (t :badge/endorsement))
           (str  endorsement-count " " (t :badge/endorsements)))]]))))


(defn issuer-modal-link [issuer-id name]
  [:div {:class "issuer-data clearfix"}
   [:label {:class "pull-label-left"}  (t :badge/Issuedby) ":"]
   [:div {:class "issuer-links pull-label-left inline"}
    [:a {:href "#"
         :on-click #(do (.preventDefault %)
                      (mo/open-modal [:badge :issuer] issuer-id {}))} name]]])

;;;TODO creator endorsements
(defn creator-modal-link [creator-id name]
  (when (and creator-id name)
    [:div {:class "issuer-data clearfix"}
     [:label.pull-left (t :badge/Createdby) ":"]
     [:div {:class "issuer-links pull-label-left inline"}
      [:a {:href "#"
           :on-click #(do (.preventDefault %)
                        (mo/open-modal [:badge :creator] creator-id))} name]]]))

(defn num-days-left [timestamp]
  (int (/ (- timestamp (/ (.now js/Date) 1000)) 86400)))

(defn save-raiting [id state init-data raiting]
  (ajax/POST
    (path-for (str "/obpv1/badge/save_raiting/" id))
    {:params   {:rating  (if (pos? raiting) raiting nil)}
     :handler (fn []
                (init-data state id))}))


(defn follow-verified-bar [{:keys [verified_by_obf issued_by_obf badge_id obf_url]} context show-messages]
  (let [visibility (if show-messages "hidden" "visible")]
    (if (= context "gallery")
      [:div.row.flip
       (if (or verified_by_obf issued_by_obf)
         (bh/issued-by-obf obf_url verified_by_obf issued_by_obf)
         [:div.col-md-3])
       [:div.col.md-9.text-right {:style {:padding-right "10px" :margin-right "auto" :visibility visibility}}
        [follow-badge badge_id]]]
      [:div.row.flip {:style {:padding "10px"}}
       [:div.col-md-3]
       [:div.col.md-9.pull-right {:style {:padding-right "10px" :margin-bottom "10px" :margin-right "auto"}}
        [follow-badge badge_id]]])))


(defn below-image-block [state endorsement_count]
  (let [{:keys [id view_count owner? badge_id message_count user-logged-in? congratulated? expires_on revoked user_endorsement_count]} @state
        invalid? (or (bh/badge-expired? expires_on) (pos? revoked))]
    [:div.badge-info-container
     ;view count
     (when (and owner? (pos? view_count))
       [:div.row {:id "badge-rating"}
        [:div.view-count
         [:i {:class "fa fa-eye"}]
         (cond
           (= view_count 1) (t :badge/Viewedonce)
           (> view_count 1) (str view_count " " (t :badge/Views) ) #_(str (t :badge/Viewed) " " view_count " " (t :badge/times)))]])
     ;congratulate
     [:div.row
      [:div {:id "badge-congratulated"}
       (if (and user-logged-in? (not owner?))
         (if congratulated?
           [:div.congratulated
            [:span [:i {:class "fa fa-heart"}]]
            (str " " (t :badge/Congratulated))]
           [:button {:class    "btn btn-primary"
                     :on-click #(congratulate state)}
            [:i {:class "fa fa-heart"}]
            (str " " (t :badge/Congratulate) "!")]))]]
     ;messages
     (when-not invalid?
       (if user-logged-in?
         [:div.row
          [badge-message-link message_count  badge_id]]))

     ;endorsements
     [:div.row (badge-endorsement-modal-link {:badge-id badge_id :id id} endorsement_count user_endorsement_count)]]))



(defn badge-content [state]
  (let [{:keys [id badge_id  owner? visibility show_evidence rating issuer_image issued_on expires_on revoked issuer_content_id issuer_content_name issuer_content_url issuer_contact issuer_description first_name last_name description criteria_url criteria_content user-logged-in? congratulated? congratulations view_count evidence_url issued_by_obf verified_by_obf obf_url recipient_count assertion creator_content_id creator_name creator_image creator_url creator_email creator_description  qr_code owner message_count issuer-endorsements content endorsement_count endorsements evidences]} @state
        expired? (bh/badge-expired? expires_on)
        revoked (pos? revoked)
        show-recipient-name-atom (cursor state [:show_recipient_name])
        selected-language (cursor state [:content-language])
        metabadge-fn (first (plugin-fun (session/get :plugins) "metabadge" "metabadge"))
        {:keys [name description tags alignment criteria_content image_file image_file issuer_content_id issuer_content_name issuer_content_url issuer_contact issuer_image issuer_description criteria_url  creator_name creator_url creator_email creator_image creator_description message_count endorsement_count creator_content_id]} (content-setter @selected-language content)
        evidences (remove #(= true (get-in % [:properties :hidden])) evidences)]
    [:div {:id "badge-info" :class "row flip"}
     [:div {:class "col-md-3"}
      [:div.badge-image
       [:img {:src (str "/" image_file)}]]
      [below-image-block state endorsement_count]]
     [:div {:class "col-md-9 badge-info view-tab"}
      [:div.row
       [:div {:class "col-md-12"}
        (if revoked
          [:div.revoked (t :badge/Revoked)])
        (if expired?
          [:div.expired [:label (str (t :badge/Expiredon) ":")] (date-from-unix-time (* 1000 expires_on))])
        [:h1.uppercase-header name]
        ;[metabadge-block (:assertion_url @state)]
        ;[metabadge (:assertion_url @state)]
        (if (< 1 (count (:content @state)))
          [:div.inline [:label (t :core/Languages)": "](content-language-selector selected-language (:content @state))])
        (issuer-modal-link issuer_content_id issuer_content_name)
        (creator-modal-link creator_content_id creator_name)

        (if (and issued_on (> issued_on 0))
          [:div [:label (t :badge/Issuedon) ": "]  (date-from-unix-time (* 1000 issued_on))])
        (if (and expires_on (not expired?))
          [:div [:label (t :badge/Expireson) ": "] (str (date-from-unix-time (* 1000 expires_on)) " ("(num-days-left expires_on) " " (t :badge/days)")")])

        (if (pos? @show-recipient-name-atom)
          (if (and user-logged-in? (not owner?))
            [:div [:label (t :badge/Recipient) ": " ] [:a {:href (path-for (str "/profile/" owner))} first_name " " last_name]]
            [:div [:label (t :badge/Recipient) ": "]  first_name " " last_name]))
        ;metabadges
        (if (and owner? metabadge-fn) [:div [metabadge-fn (:assertion_url @state)]])

        [:div.description description]


        ;;Check-badge
        (check-badge id)

        ;;Endorse-badge
        (when-not owner? [endr/endorse-badge id])]]


      (when-not (empty? alignment)
        [:div.row
         [:div.col-md-12
          [:h2.uppercase-header (t :badge/Alignments)]
          (doall
            (map (fn [{:keys [name url description]}]
                   [:p {:key url}
                    [:a {:target "_blank" :rel "noopener noreferrer" :href url} name] [:br] description])
                 alignment))]])

      [:div {:class "row criteria-html"}
       [:div.col-md-12
        [:h2.uppercase-header (t :badge/Criteria)]
        [:a {:href criteria_url :target "_blank"} (t :badge/Opencriteriapage) "..."]
        [:div {:dangerouslySetInnerHTML {:__html criteria_content}}]]]


      (when (seq evidences)
        [:div.row {:id "badge-settings"}
         [:div.col-md-12
          [:h2.uppercase-header (t :badge/Evidences) #_(if (= (count  evidences) 1)  (t :badge/Evidence) (str (t :badge/Evidence) " (" (count evidences) ")"))]
          (reduce (fn [r evidence]
                    (let [{:keys [narrative description name id url mtime ctime properties]} evidence
                          added-by-user? (and (not (blank? description)) (starts-with? description "Added by badge recipient")) ;;use regex
                          {:keys [resource_id resource_type mime_type hidden]} properties
                          desc (cond
                                 (not (blank? narrative)) narrative
                                 (not added-by-user?) description ;;todo use regex to match description
                                 :else nil)]

                      (conj r (when (and (not hidden) (url? url))
                                [:div.modal-evidence
                                 (when-not added-by-user? [:span.label.label-success (t :badge/Verifiedevidence)])
                                 [evidence-icon {:type resource_type :mime_type mime_type}]
                                 [:div.content

                                  (when-not (blank? name) [:div.content-body.name name])
                                  (when-not (blank? desc) [:div.content-body.description {:dangerouslySetInnerHTML {:__html desc}}])
                                  [:div.content-body.url
                                   (case resource_type
                                     "file" (hyperlink url)
                                     "page" (if (session/get :user)
                                              [:a {:href "#"
                                                   :on-click #(do
                                                                (.preventDefault %)
                                                                (mo/open-modal [:page :view] {:page-id resource_id}))} url]
                                              (hyperlink url))
                                     (hyperlink url))]]]))))

                  [:div ] evidences)]])

      (into [:div]
            (for [f (plugin-fun (session/get :plugins) "block" "badge_info")]
              [f id]))]]))


(defn modal-navi [state]
  (let [invalid? (or (bh/badge-expired? (:expires_on @state)) (pos? (:revoked @state)))
        selected-language (cursor state [:content-language])
        data (content-setter @selected-language (:content @state))
        disable-link (if invalid? "btn disabled")
        disable-export (if (or (private?) invalid?) "btn disabled")]
    [:div.col-md-9.badge-modal-navi
     [:ul {:class "nav nav-tabs wrap-grid"}
      [:li.nav-item{:class  (if (or (nil? (:tab-no @state))(= 1 (:tab-no @state))) "active")}
       [:a.nav-link {:href "#" :on-click #(do (init-data state (:id @state) 1) #_(swap! state assoc :tab [badge-content state] :tab-no 1))}
        [:div  [:i.nav-icon {:class "fa fa-eye fa-lg"}] (t :page/View)]]]
      [:li.nav-item {:class (if (= 2 (:tab-no @state)) "active")}
       [:a.nav-link {:class disable-link :href "#" :on-click #(show-settings-dialog (:id @state) state init-data "settings")}
        [:div  [:i.nav-icon {:class "fa fa-cogs fa-lg"}] (t :page/Settings)]]]
      [:li.nav-item {:class (if (= 3 (:tab-no @state)) "active")}
       [:a.nav-link {:class disable-link :href "#" :on-click #(show-settings-dialog (:id @state) state init-data "share")}
        [:div  [:i.nav-icon {:class "fa fa-share-alt fa-lg"}] (t :badge/Share)]]]
      [:li.nav-item {:class (if (= 4 (:tab-no @state)) "active")}
       [:a.nav-link {:class disable-export  :href "#" :on-click #(swap! state assoc :tab [se/download-tab-content (assoc data :assertion_url (:assertion_url @state)
                                                                                                                    :obf_url (:obf_url @state)) state] :tab-no 4)}
        [:div  [:i.nav-icon {:class "fa fa-download fa-lg"}] (t :core/Download)]]]
      [:li.nav-item {:class (if (= 5 (:tab-no @state)) "active")}
       [:a.nav-link.delete-button {:href "#" :on-click #(do
                                                          (swap! state assoc-in [:badge-settings :confirm-delete?] true)
                                                          (swap! state assoc :tab [se/delete-tab-content data state] :tab-no 5))}
        [:i.nav-icon {:class "fa fa-trash fa-lg"}] (t :core/Delete)]]]]))


(defn modal-top-bar [state]
  (let [{:keys [verified_by_obf issued_by_obf obf_url owner? ]} @state]
    [:div.row.flip
     (if (or verified_by_obf issued_by_obf)
       (bh/issued-by-obf obf_url verified_by_obf issued_by_obf)
       [:div.col-md-3])
     (if owner? [modal-navi state])]))


(defn content [state]
  (let [{:keys [id owner? tab user-logged-in?]} @state
        selected-language (cursor state [:content-language])
        data (content-setter @selected-language (:content @state))]
    [:div
     (when (and (not owner?) user-logged-in?) [follow-verified-bar @state nil nil])
     [modal-top-bar state]
     (if tab tab [badge-content state])
     (if (and owner? (session/get :user)) "" [reporttool1 id  (:name data) "badge"])]))



(defn handler [params]

  (let [id (:badge-id params)
        data (:data params)
        state (atom {:initializing true
                     :permission "initial"})
        user (session/get :user)]
    (if data (init-data state id nil data) (init-data state id nil))

    (fn []
      (cond
        (= "initial" (:permission @state)) [:div]
        (and user (= "error" (:permission @state))) (err/error-content)
        (= "error" (:permission @state)) (err/error-content)
        (and (= "success" (:permission @state)) (:owner? @state) user) (content state)
        (and (= "success" (:permission @state)) user) (content state)
        :else (content state)))))



(def ^:export modalroutes
  {:badge {:info handler
           :metadata a/assertion-content
           :endorsement endr/badge-endorsement-content
           :issuer issuer/content
           :creator issuer/creator-content
           :linkedin1 s/linkedin-modal1
           :linkedin2 s/content-modal-render
           :endorse endr/endorse-badge-content
           :userbadgeendorsement endr/user-badge-endorsement-content
           :userendorsement endr/user-endorsement-content
           :my block/mybadgesmodal}})
