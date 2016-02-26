(ns salava.badge.ui.info
  (:require [reagent.core :refer [atom cursor]]
            [reagent.session :as session]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.core.ui.layout :as layout]
            [salava.core.i18n :refer [t]]
            [salava.badge.ui.helper :as bh]
            [salava.core.ui.rate-it :as r]
            [salava.core.ui.share :as s]
            [salava.core.ui.helper :as h]
            [salava.core.time :refer [date-from-unix-time unix-time]]))

(defn toggle-visibility [state]
  (let [id (:id @state)
        new-value (if (= (:visibility @state) "private") "public" "private")]
    (ajax/POST
      (str "/obpv1/badge/set_visibility/" id)
      {:params {:visibility new-value}
       :handler (fn [] (swap! state assoc :visibility new-value))})))

(defn toggle-recipient-name [state]
  (let [id (:id @state)
        new-value (not (:show_recipient_name @state))]
    (ajax/POST
      (str "/obpv1/badge/toggle_recipient_name/" id)
      {:params {:show_recipient_name new-value}
       :handler (fn [] (swap! state assoc :show_recipient_name new-value))})))

(defn toggle-evidence [state]
  (let [id (:id @state)
        new-value (not (:show_evidence @state))]
    (ajax/POST
      (str "/obpv1/badge/toggle_evidence/" id)
      {:params {:show_evidence new-value}
       :handler (fn [] (swap! state assoc :show_evidence new-value))})))

(defn congratulate [state]
  (ajax/POST
    (str "/obpv1/badge/congratulate/" (:id @state))
    {:handler (fn [] (swap! state assoc :congratulated? true))}))

(defn content [state]
  (let [{:keys [id name owner? visibility show_recipient_name show_evidence image_file rating issued_on expires_on issuer_content_name issuer_content_url issuer_contact first_name last_name description criteria_url html_content user-logged-in? congratulated? congratulations view_count evidence_url issued_by_obf verified_by_obf obf_url recipient_count]} @state
        expired? (and expires_on (< expires_on (unix-time)))]
    [:div {:id "badge-info"}
     [:div.panel
      [:div.panel-body
       (if (and owner? (not expired?))
         [:div.row
          [:div.col-sm-3
           [:div.checkbox
            [:label
             [:input {:type "checkbox"
                      :on-change #(toggle-visibility state)
                      :checked (= visibility "public")}]
             (t :core/Publishandshare)]]]
          [:div.col-sm-3
           [:div.checkbox
            [:label
             [:input {:type "checkbox"
                      :on-change #(toggle-recipient-name state)
                      :checked show_recipient_name}]
             (t :badge/Showyourname)]]]
          [:div.col-sm-3
           [:div.checkbox
            [:label
             [:input {:type "checkbox"
                      :on-change #(toggle-evidence state)
                      :checked show_evidence}]
             (t :badge/Showevidence)]]]
          [:div {:class "col-sm-3 text-right"}
           [:button {:class "btn btn-primary"
                     :on-click #(.print js/window)}
            (t :core/Print)]]
          [:div.col-sm-12
           [s/share-buttons (str (session/get :site-url) "/badge/info/" id) name (= "public" visibility) true (cursor state [:show-link-or-embed])]]])
       (if (or verified_by_obf issued_by_obf)
         (bh/issued-by-obf obf_url verified_by_obf issued_by_obf))
       [:div.row
        [:div {:class "col-md-3 badge-image"}
         [:div.row
          [:div.col-xs-12
           [:img {:src (str "/" image_file)}]]]
         (if owner?
           [:div.row
            [:div.col-xs-12
             [:div.rating
              [r/rate-it rating]]
             [:div.view-count
              (cond
                (= view_count 1) (t :badge/Viewedonce)
                (> view_count 1) (str (t :badge/Viewed) " " view_count " " (t :badge/times))
                :else (t :badge/Badgeisnotviewedyet))]]])
         (if (> recipient_count 0)
           [:div.row
            [:div.col-xs-12
             [:a {:href "#"} (t :badge/Otherrecipients)]]])
         [:div.row
          [:div.col-xs-12
           (if (and user-logged-in? (not owner?))
             (if congratulated?
               [:div.congratulated
                [:i {:class "fa fa-heart"}]
                (str " " (t :badge/Congratulated))]
               [:button {:class "btn btn-primary"
                         :on-click #(congratulate state)}
                [:i {:class "fa fa-heart"}]
                (str " " (t :badge/Congratulations) "!")])
             )]]]
        [:div {:class "col-md-9 badge-info"}
         [:div.row
          [:div {:class "col-md-12"}
           (if expired?
             [:div.expired (t :badge/Expiredon) ": " (date-from-unix-time (* 1000 expires_on))])
           [:h1.uppercase-header name]
           (if (and issued_on (> issued_on 0))
             [:div [:label (t :badge/Issuedon)] ": " (date-from-unix-time (* 1000 issued_on))])
           (if (and expires_on (> expires_on (unix-time)))
             [:div [:label (t :badge/Expireson)] ": " (date-from-unix-time (* 1000 expires_on))])
           (bh/issuer-label-and-link issuer_content_name issuer_content_url issuer_contact)
           (if show_recipient_name
             [:div [:label (t :badge/Recipient)] ": " first_name " " last_name])
           [:div.description description]
           [:h2.uppercase-header (t :badge/Criteria)]
           [:a {:href criteria_url :target "_blank"} (t :badge/Opencriteriapage) "..."]]]
         [:div {:class "row evidence-html"}
          [:div.col-md-12
           {:dangerouslySetInnerHTML {:__html html_content}}]]
         (if (and show_evidence evidence_url)
           [:div.row
            [:div.col-md-12
             [:h2.uppercase-header (t :badge/Evidence)]
             [:div [:a {:target "_blank" :href evidence_url} (t :badge/Openevidencepage) "..."]]]])
         (if (and owner? (not-empty congratulations))
           [:div.row
            [:div.col-md-12
             [:h3.congratulated-header
              [:i {:class "fa fa-heart"}]
              " " (t :badge/Congratulatedby) ":"]
             (into [:div ]
                   (for [congratulation congratulations
                         :let [{:keys [id first_name last_name profile_picture]} congratulation]]
                     [:li.badge-congratulation
                      [:a {:href (str "/user/profile/" id)}
                       [:img {:src (h/profile-picture profile_picture)}]
                       first_name " " last_name]]))]])]]]]]))

(defn init-data [state id]
  (ajax/GET
    (str "/obpv1/badge/info/" id)
    {:handler (fn [data]
                (reset! state (assoc data :id id
                                          :show-link-or-embed-code nil)))}))


(defn handler [site-navi params]
  (let [id (:badge-id params)
        state (atom {})]
    (init-data state id)
    (fn []
      (if (session/get :user)
        (layout/default site-navi (content state))
        (layout/landing-page (content state))))))

