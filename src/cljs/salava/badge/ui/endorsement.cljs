(ns salava.badge.ui.endorsement
  (:require [salava.core.i18n :refer [t]]
            [reagent.core :refer [atom cursor create-class]]
            [reagent.dom :as reagent]
            [salava.core.time :refer [unix-time date-from-unix-time]]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.core.ui.helper :refer [path-for private?]]
            [salava.core.ui.modal :as mo]
            [clojure.string :refer [blank?]]
            [reagent.session :as session]
            [salava.user.ui.helper :refer [profile-picture]]
            [reagent-modals.modals :as m]
            [salava.core.ui.layout :as layout]
            [salava.core.ui.error :as err]
            [salava.core.ui.grid :as g]
            [cljsjs.simplemde]))


(defn endorsement-row [endorsement]
  (let [{:keys [issuer content issued_on]} endorsement]

    [:div {:style {:margin-bottom "20px"}}
     [:h5
      (when (:image_file issuer) [:img {:src (str "/" (:image_file issuer)) :style {:width "55px" :height "auto" :padding "7px"}}])
      [:a {:href "#"
           :on-click #(do (.preventDefault %) (mo/set-new-view [:badge :issuer] (:id issuer)))
           } (:name issuer)]
      " "
      [:small (date-from-unix-time (* 1000 issued_on))]]
     [:div {:dangerouslySetInnerHTML {:__html content}}]]))


(defn init-badge-endorsements [state badge-id]
  (ajax/GET
    (path-for (str "/obpv1/badge/endorsement/" badge-id))
    {:handler (fn [data] (reset! state data))}))

(defn init-user-badge-endorsement [state]
  (ajax/GET
    (path-for (str "/obpv1/badge/user/endorsement/" (:id @state)))
    {:handler (fn [data]
                (swap! state assoc :user-badge-endorsements data)
                (when (some #(= (:issuer_id %) (:endorser-id @state)) data)
                  (swap! state assoc :show-link "none"
                         :show-content "none"
                         :show-endorsement-status "block")))}))

(defn user-badge-endorsement-content [badge-id badge-endorsements]
  (let [state (atom {:id badge-id})]
    (init-user-badge-endorsement state)
    (fn []
      (let [endorsements (filter #(= (:status %) "accepted") (:user-badge-endorsements @state))
            badge-endorsements? (pos? (count @badge-endorsements))]
        (when (seq endorsements)
          [:div
           (if badge-endorsements? [:hr.line])
           [:h4 {:style {:margin-bottom "20px"}} (t :badge/BadgeEndorsedByIndividuals)]
           (reduce (fn [r endorsement]
                     (let [{:keys [id user_badge_id image_file name content issuer_name profile_picture issuer_id mtime]} endorsement]
                       (conj r [:div {:style {:margin-bottom "20px"}}

                                [:h5
                                 [:img {:src (profile-picture profile_picture) :style {:width "55px" :height "auto" :padding "7px"}}]
                                 (if issuer_id [:a {:href "#"
                                                    :on-click #(do (.preventDefault %) (mo/set-new-view [:profile :view] {:user-id issuer_id}))
                                                    } issuer_name] issuer_name)
                                 " "
                                 [:small (date-from-unix-time (* 1000 mtime))]]
                                [:div {:dangerouslySetInnerHTML {:__html content}}]]
                             ))) [:div] endorsements)]
          ;]
          )))))

(defn badge-endorsement-content [param]
  (let [endorsements (atom [])
        badge-id (if (map? param) (:badge-id param) param)
        user-badge-id (:id param)]
    (init-badge-endorsements endorsements badge-id)
    (fn []
      (let [endorsement-count (count @endorsements)]
        [:div.row {:id "badge-contents"}
         (when (seq @endorsements)
           [:div.col-xs-12
            [:h4 {:style {:margin-bottom "20px"}} (t :badge/BadgeEndorsedByOrganizations)]
            (into [:div]
                  (for [endorsement @endorsements]
                    (endorsement-row endorsement)))])
         (when user-badge-id
           [:div.col-xs-12
            [user-badge-endorsement-content user-badge-id endorsements]]
           )]))))



;; User Badge Endorsements

(defn init-user-endorsements [state]
  (ajax/GET
    (path-for (str "/obpv1/badge/user/endorsements"))
    {:handler (fn [data]
                (reset! state (assoc data
                                :initializing false
                                :permission "success"
                                :show (session/get! :visible-area "all")
                                :search ""
                                :order "mtime"))
                )}
    (fn [] (swap! state assoc :permission "error"))))



(defn init-pending-endorsements [state]
  (ajax/GET
    (path-for "/obpv1/badge/user/pending_endorsement/")
    {:handler (fn [data]
                (swap! state assoc :pending data)
                )}))

(defn edit-endorsement [id badge-id content]
  (ajax/POST
    (path-for (str "/obpv1/badge/endorsement/edit/" id))
    {:params {:content content
              :user_badge_id badge-id}
     :handler (fn [data]
                (when (= "success" (:status data))
                  ))}))

(defn save-endorsement [state]
  (ajax/POST
    (path-for (str "/obpv1/badge/endorsement/" (:id @state)))
    {:params {:content @(cursor state [:endorsement-comment]) }
     :handler (fn [data]
                (when (= (:status data) "success")
                  (swap! state assoc :show-link "none"
                         :show-content "none"
                         :show-endorsement-status "block")
                  ))}))

(defn update-status [id status user_badge_id state reload-fn]
  (ajax/POST
    (path-for (str "/obpv1/badge/endorsement/update_status/" id))
    {:params {:user_badge_id user_badge_id
              :status status}
     :handler (fn [data]
                (when (= "success" (:status data))
                  (when reload-fn (reload-fn state))))}))

(defn delete-endorsement [id user_badge_id state reload-fn]
  (ajax/DELETE
    (path-for (str "/obpv1/badge/endorsement/" user_badge_id "/" id))
    {:handler (fn [data]
                (when (= "success" (:status data))
                  (when reload-fn (reload-fn state))))}))




(def simplemde-toolbar (array "bold" "italic" "heading-3"
                              "quote" "unordered-list" "ordered-list"
                              "link" "horizontal-rule"
                              "preview"))

(def editor (atom nil))

(defn init-editor [element-id value]
  (reset! editor (js/SimpleMDE. (clj->js {:element (.getElementById js/document element-id)
                                          :toolbar simplemde-toolbar
                                          :spellChecker false})))
  (.value @editor @value)
  (js/setTimeout (fn [] (.value @editor @value)) 200)
  (.codemirror.on @editor "change" (fn [] (reset! value (.value @editor)))))


(defn markdown-editor [value]
  (create-class {:component-did-mount (fn []
                                        (init-editor (str "editor" (-> (session/get :user) :id)) value))
                 :reagent-render (fn []
                                   [:div.form-group {:style {:display "block"}}
                                    [:textarea {:class "form-control"
                                                :id (str "editor" (-> (session/get :user) :id))
                                                :defaultValue @value
                                                :on-change #(reset! value (.-target.value %))
                                                }]])}))

(defn process-text [s state]
  (let [text (-> js/document
                 (.getElementById (str "editor" (-> (session/get :user) :id)))
                 (.-innerHTML))
        endorsement-claim (str text (if (blank? text) "" "\n\n") "* " s)]
    (reset! (cursor state [:endorsement-comment]) endorsement-claim)
    (.value @editor  @(cursor state [:endorsement-comment]))))


(defn endorse-badge-content [state]
  (fn []
    [:div {:style {:display @(cursor state [:show-content])}}
     [:hr.border]
     [:div.row
      [:div.col-xs-12 {:style {:margin-bottom "10px"}} [:a.close {:href "#" :on-click #(do
                                                                                         (.preventDefault %)
                                                                                         (swap! state assoc :show-link "block"
                                                                                                :show-content "none"))} [:i.fa.fa-remove {:title (t :core/Cancel)}]]]]

     [:div.endorse {:style {:margin "5px"}} (t :badge/Endorsehelptext)]

     [:div.row
      [:div.col-xs-12
       [:div.list-group
        [:a.list-group-item {:id "phrase1" :href "#" :on-click #(do
                                                                  (.preventDefault %)
                                                                  (process-text (t :badge/Endorsephrase1) state)
                                                                  )} [:i.fa.fa-plus-circle][:span (t :badge/Endorsephrase1)]]
        [:a.list-group-item {:href "#" :on-click #(do
                                                    (.preventDefault %)
                                                    (process-text (t :badge/Endorsephrase2) state)
                                                    )} [:i.fa.fa-plus-circle][:span (t :badge/Endorsephrase2)]]
        [:a.list-group-item {:href "#" :on-click #(do
                                                    (.preventDefault %)
                                                    (process-text (t :badge/Endorsephrase3) state)
                                                    ) } [:i.fa.fa-plus-circle][:span (t :badge/Endorsephrase3)]]
        [:a.list-group-item {:href "#" :on-click #(do
                                                    (.preventDefault %)
                                                    (process-text (t :badge/Endorsephrase4) state)
                                                    ) } [:i.fa.fa-plus-circle][:span (t :badge/Endorsephrase4)]]
        [:a.list-group-item {:href "#" :on-click #(do
                                                    (.preventDefault %)
                                                    (process-text (t :badge/Endorsephrase5) state)
                                                    )  } [:i.fa.fa-plus-circle][:span (t :badge/Endorsephrase5)]]
        [:a.list-group-item {:href "#" :on-click #(do
                                                    (.preventDefault %)
                                                    (process-text (t :badge/Endorsephrase6) state)
                                                    )  } [:i.fa.fa-plus-circle][:span (t :badge/Endorsephrase6)]]]]]

     [:div.editor
      [:div.form-group
       [:label {:for "claim"} (str (t :badge/Composeyourendorsement) ":") ]
       [:div [markdown-editor (cursor state [:endorsement-comment]) (str "editor" (-> (session/get :user) :id))]]]
      [:div
       [:button.btn.btn-primary {:on-click #(do
                                              (.preventDefault %)
                                              (save-endorsement state))
                                 :disabled (blank? @(cursor state [:endorsement-comment]))

                                 } (t :badge/Endorsebadge)]]
      [:hr.border]]]))

(defn endorsement-text [state]
  (let [user-endorsement (->> @(cursor state [:user-badge-endorsements])
                              (filter #(= (:endorser-id @state) (:issuer_id %))))]
    (if (seq user-endorsement)
      (case  (->> user-endorsement first :status)
        "accepted" [:span.label.label-success (t :badge/Youendorsebadge)]
        "declined" [:span.label.label-danger (t :badge/Declinedendorsement)]
        [:span.label.label-info (t :badge/Pendingendorsement)]
        )
      [:span.label.label-info (t :badge/Pendingendorsement)])))

(defn endorse-badge-link [state]
  (fn []
    [:div
     [:a {:href "#"
          :style {:display @(cursor state [:show-link])}
          :on-click #(do
                       (.preventDefault %)
                       (swap! state assoc :show-link "none"
                              :show-content "block")
                       )}[:i.fa.fa-thumbs-o-up {:style {:vertical-align "unset"}}] (t :badge/Endorsethisbadge)]
     [:div {:style {:display @(cursor state [:show-endorsement-status])}} [:i.fa.fa-thumbs-up] (endorsement-text state)]]))

(defn profile-link-inline [id issuer_name picture]
  [:div [:a {:href "#"
             :on-click #(mo/open-modal [:profile :view] {:user-id id})}
         [:img {:src (profile-picture picture)}]
         (str issuer_name " ")]  (t :badge/Hasendorsedyou)])

(defn pending-endorsements []
  (let [state (atom {:user-id (-> (session/get :user) :id)})]
    (init-pending-endorsements state)
    (fn []
      [:div#endorsebadge
       (reduce (fn [r endorsement]
                 (let [{:keys [id user_badge_id image_file name content profile_picture issuer_id description issuer_name]} endorsement]
                   (conj r
                         [:div
                          [:div.col-md-12
                           [:div.thumbnail
                            [:div.endorser.col-md-12
                             [profile-link-inline issuer_id issuer_name profile_picture id]
                             [:hr.line]
                             ]
                            [:div.caption.row.flip
                             [:div.position-relative.badge-image.col-md-3
                              [:img {:src (str "/" image_file) :style {:padding "15px"}}]]

                             [:div.col-md-9 [:h4.media-heading name]
                              [:div.thumbnail-description.smaller {:dangerouslySetInnerHTML {:__html content}}]]
                             ]

                            [:div.caption.card-footer.text-center
                             [:hr.line]
                             [:button.btn.btn-primary {:href "#"
                                                       :on-click #(do
                                                                    (.preventDefault %)
                                                                    (update-status id "accepted" user_badge_id state init-pending-endorsements)
                                                                    )} (t :badge/Acceptendorsement)]
                             [:button.btn.btn-warning.cancel {:href "#"
                                                              :on-click #(do
                                                                           (.preventDefault %)
                                                                           (update-status id "declined" user_badge_id state init-pending-endorsements))} (t :badge/Declineendorsement)]
                             #_[:ul.list-inline.buttons
                                [:li {:style {:margin "10px"}} [:a.button {:href "#"
                                                                           :on-click #(do
                                                                                        (.preventDefault %)
                                                                                        (update-status id "accepted" user_badge_id state init-pending-endorsements)
                                                                                        )} [:i.fa.fa-check] (t :badge/Acceptendorsement)]]
                                [:li.cancel {:style {:margin "10px"}}[:a.button {:href "#"
                                                                                 :on-click #(do
                                                                                              (.preventDefault %)
                                                                                              (update-status id "declined" user_badge_id state init-pending-endorsements))} [:i.fa.fa-remove] (t :badge/Declineendorsement)]]]]]]]))) [:div.row] @(cursor state [:pending]))
       ])))

(defn endorse-badge [badge-id]
  (let [state (atom {:id badge-id
                     :show-link "block"
                     :show-content "none"
                     :endorsement-comment ""
                     :endorser-id (-> (session/get :user) :id)
                     :show-endorsement-status "none"})]
    (init-user-badge-endorsement state)
    (fn []
      [:div#endorsebadge {:style {:margin-top "10px"}}
       [endorse-badge-link state]
       [endorse-badge-content state]])))

(defn endorsement-list [badge-id]
  (let [state (atom {:id badge-id})]
    (init-user-badge-endorsement state)
    (fn []
      (when (seq @(cursor state [:user-badge-endorsements]))
        [:div
         [:div.row
          [:label.col-md-12.sub-heading (t :badge/Endorsements)]]
         [:div#endorsebadge

          (reduce (fn [r endorsement]
                    (let [{:keys [id user_badge_id image_file name content issuer_name first_name last_name profile_picture issuer_id issuer_name status]} endorsement]
                      (conj r [:div.panel.panel-default.endorsement
                               [:div.panel-heading {:id (str "heading" id)}
                                [:div.panel-title
                                 (if (= "pending" status)  [:span.label.label-info (t :social/Pending)])
                                 [:div.row.flip.settings-endorsement
                                  [:div.col-md-9
                                   (if issuer_id
                                     [:a {:href "#"
                                        :on-click #(mo/open-modal [:profile :view] {:user-id issuer_id})}
                                    [:img.small-image {:src (profile-picture profile_picture)}]
                                    issuer_name] [:div [:img.small-image {:src (profile-picture profile_picture)}] issuer_name])]]]

                                [:div [:button {:type "button"
                                                :aria-label "OK"
                                                :class "close"
                                                :on-click #(do (.preventDefault %)
                                                             (delete-endorsement id user_badge_id state init-user-badge-endorsement))
                                                }
                                       [:i.fa.fa-trash.trash]]]]
                               [:div.panel-body
                                [:div {:dangerouslySetInnerHTML {:__html content}}]
                                (when (= "pending" status)
                                  [:div.caption
                                   [:hr.line]
                                   [:div.text-center
                                    [:ul.list-inline.buttons.buttons
                                     [:button.btn.btn-primary {:href "#"
                                                               :on-click #(do
                                                                            (.preventDefault %)
                                                                            (update-status id "accepted" user_badge_id state init-user-badge-endorsement)
                                                                            )} (t :badge/Acceptendorsement)]
                                     [:button.btn.btn-warning.cancel {:href "#"
                                                                      :on-click #(do
                                                                                   (.preventDefault %)
                                                                                   (update-status id "declined" user_badge_id state init-user-badge-endorsement))} (t :badge/Declineendorsement)]
                                     #_[:a.button {:href "#"
                                                   :on-click #(do
                                                                (.preventDefault %)
                                                                (update-status id "accepted" user_badge_id state init-user-badge-endorsement)
                                                                )} [:li [:i.fa.fa-check {:title (t :badge/Acceptendorsement)}] ]]
                                     #_[:a.button {:href "#"
                                                   :on-click #(do
                                                                (.preventDefault %)
                                                                (update-status id "declined" user_badge_id state init-user-badge-endorsement))}[:li.cancel [:i.fa.fa-remove {:title (t :badge/Declineendorsement)}]]]]]])
                                ]]))

                    ) [:div] @(cursor state [:user-badge-endorsements]))]]))))

(defn profile [element-data]
  (let [{:keys [id first_name last_name profile_picture status label issuer_name]} element-data
        current-user (session/get-in [:user :id])]
    [:div.endorsement-profile.panel-default
     (if id
       [:a {:href "#" :on-click #(mo/open-modal [:profile :view] {:user-id id})}
        [:div.panel-body.flip
         [:div.col-md-4
          [:div.profile-image
           [:img.img-responsive.img-thumbnail
            {:src (profile-picture profile_picture)
             :alt (or issuer_name (str first_name " " last_name))}]]]
         [:div.col-md-8
          [:h4 (or issuer_name (str first_name " " last_name))]
          (when (= status "pending") [:p [:span.label.label-info label]])]]]
       [:div.panel-body.flip
        [:div.col-md-4
         [:div.profile-image
          [:img.img-responsive.img-thumbnail
           {:src (profile-picture profile_picture)
            :alt (or issuer_name (str first_name " " last_name))}]]]
        [:div.col-md-8
         [:h4 (or issuer_name (str first_name " " last_name))]
         (when (= status "pending") [:p [:span.label.label-info label]])]]
       )]))

(defn user-endorsement-content [params]
  (fn []
    (let [{:keys [endorsement state]} @params
          {:keys [id profile_picture name first_name last_name image_file content user_badge_id issuer_id issuer_name endorsee_id status]} endorsement]
      [:div.row.flip {:id "badge-info"}
       [:div.col-md-3
        [:div.badge-image [:img.badge-image {:src (str "/" image_file)}]]]
       [:div.col-md-9
        [:div
         [:h1.uppercase-header name]-
         [:div (if endorsee_id (t :badge/Manageendorsementtext1) (t :badge/Manageendorsementtext2))]
         [:hr.line]
         [:div.row
          [:div.col-md-4.col-md-push-8  " "]
          [:div.col-md-8.col-md-pull-4 [profile {:id (or endorsee_id issuer_id)
                                                 :profile_picture profile_picture
                                                 :first_name first_name
                                                 :last_name last_name
                                                 :issuer_name issuer_name
                                                 :status status
                                                 :label (t :social/pending) #_(if issuer_id
                                                                                (t :badge/pendingreceived)
                                                                                (t :badge/pendinggiven)
                                                                                )}]]]

         (if endorsee_id
           [:div {:style {:margin-top "15px"}}
            [:div;.form-group
             [:label {:for "claim"} (str (t :badge/Composeyourendorsement) ":")]
             [:div.editor [markdown-editor (cursor params [:endorsement :content])]]]
            [:div.row.flip.control-buttons
             [:div.col-md-6.col-sm-6.col-xs-6.left-buttons [:button.btn.btn-primary {:on-click #(do
                                                                                                  (.preventDefault %)
                                                                                                  (edit-endorsement id user_badge_id @(cursor params [:endorsement :content])))
                                                                                     :disabled (blank? @(cursor params [:endorsement :content]))
                                                                                     :data-dismiss "modal"

                                                                                     } (t :core/Save)]
              [:button.btn.btn-warning.cancel {:data-dismiss "modal"} (t :core/Cancel)]]
             [:div.col-md-6.col-sm-6.col-xs-6.left-buttons [:a.delete-btn {:style {:line-height "4" :cursor "pointer"}
                                                                           :on-click #(do
                                                                                        (.preventDefault %)
                                                                                        (delete-endorsement id user_badge_id nil nil))
                                                                           :data-dismiss "modal"} [:i.fa.fa-trash] (t :badge/Deleteendorsement)]]]]


           [:div {:style {:margin-top "15px"}}
            [:div {:dangerouslySetInnerHTML {:__html content}}]

            [:div.caption
             [:hr.line]
             (if (= "pending" status)
               [:div.buttons
                [:button.btn.btn-primary {:href "#"
                                          :on-click #(do
                                                       (.preventDefault %)
                                                       (update-status id "accepted" user_badge_id state init-pending-endorsements)
                                                       )
                                          :data-dismiss "modal"}  (t :badge/Acceptendorsement)]
                [:button.btn.btn-warning.cancel {:href "#"
                                                 :on-click #(do
                                                              (.preventDefault %)
                                                              (update-status id "declined" user_badge_id state init-pending-endorsements))
                                                 :data-dismiss "modal"} (t :badge/Declineendorsement)]
                ;[:ul.list-inline.buttons
                #_[:ul.list-inline.buttons
                   [:li [:a.button {:href "#"
                                    :on-click #(do
                                                 (.preventDefault %)
                                                 (update-status id "accepted" user_badge_id nil nil)
                                                 )
                                    :data-dismiss "modal"}  [:i.fa.fa-check] (t :badge/Acceptendorsement)]]
                   [:li.cancel [:a.button {:href "#"
                                           :on-click #(do
                                                        (.preventDefault %)
                                                        (update-status id "declined" user_badge_id nil nil ))
                                           :data-dismiss "modal"} [:i.fa.fa-remove] (t :badge/Declineendorsement)]]]]
               [:div.row.flip.control-buttons
                [:div.col-md-6.col-sm-6.col-xs-6  [:button.btn.btn-primary.cancel {:data-dismiss "modal"} (t :core/Cancel)]]
                [:div.col-md-6.col-sm-6.col-xs-6 [:a.delete-btn {:style {:line-height "4" :cursor "pointer"}
                                                                 :on-click #(do
                                                                              (.preventDefault %)
                                                                              (delete-endorsement id user_badge_id nil nil))
                                                                 :data-dismiss "modal"} [:i.fa.fa-trash] (t :badge/Deleteendorsement)]]])]])]]])))


(defn endorsements [state]
  (let [endorsements (case @(cursor state [:show])
                       "all" @(cursor state [:all-endorsements])
                       "given" @(cursor state [:given])
                       "received" @(cursor state [:received])
                       @(cursor state [:all-endorsements]))
        processed-endorsements (if (blank? @(cursor state [:search]))
                                 endorsements
                                 (filter #(or (re-find (re-pattern (str "(?i)" @(cursor state [:search]))) (:name %))
                                              (re-find (re-pattern (str "(?i)" @(cursor state [:search]))) (get % :first_name ""))
                                              (re-find (re-pattern (str "(?i)" @(cursor state [:search]))) (get % :last_name ""))
                                              (re-find (re-pattern (str "(?i)" @(cursor state [:search]))) (get % :issuer_name ""))
                                              (re-find (re-pattern (str "(?i)" @(cursor state [:search]))) (str (:first_name %) " " (:last_name %))))
                                         endorsements))
        order (keyword  @(cursor state [:order]))
        endorsements (case order
                       (:mtime) (sort-by order > processed-endorsements)
                       (:name) (sort-by (comp clojure.string/upper-case str order) processed-endorsements)
                       (:user) (sort-by #(str (:first_name %) " " (:last_name %)) processed-endorsements)
                       processed-endorsements
                       )]

    [:div.panel
     [:div.panel-heading
      [:h3
       (str (t :badge/Endorsements) ) #_[:span.badge {:style {:vertical-align "text-top"}}  (count processed-endorsements)]]
      [:br]
      [:div (case @(cursor state [:show])
              "all" (t :badge/Allendorsementstext)
              "given" (t :badge/Givenendorsementstext)
              "received" (t :badge/Receivedendorsementstext)
              (t :badge/Allendorsementstext))]
      ]
     [:div.panel-body
      [:div.table  {:summary (t :badge/Endorsements)}
       (reduce (fn [r endorsement]
                 (let [{:keys [id endorsee_id issuer_id profile_picture issuer_name first_name last_name name image_file content status user_badge_id mtime]} endorsement
                       endorser (or issuer_name (str first_name " " last_name))]
                   (conj r [:div.list-item.row.flip
                            [:a {:href "#" :on-click #(do
                                                        (.preventDefault %)
                                                        (mo/open-modal [:badge :userendorsement] (atom {:endorsement endorsement :state state}) {:hidden (fn [] (init-user-endorsements state))} ))}

                             [:div.col-md-4.col-md-push-8
                              [:small.pull-right [:i (date-from-unix-time (* 1000 mtime) "days")]]]
                             [:div.col-md-8.col-md-pull-4
                              [:div.media
                               [:div;.row
                                [:div.labels
                                 (cond
                                   issuer_id [:span.label.label-success (t :badge/Endorsedyou)]
                                   endorsee_id [:span.label.label-primary (t :badge/Youendorsed)]
                                   ;(and (not issuer_id) (not endorsee_id)) [:span.label.label-success (t :badge/Endorsedyou)]
                                   :else [:span.label.label-success (t :badge/Endorsedyou)]
                                   )
                                 #_(if issuer_id
                                   [:span.label.label-success (t :badge/Endorsedyou)]
                                   [:span.label.label-primary (t :badge/Youendorsed)])
                                 (if (= "pending" status)
                                   [:span.label.label-info
                                    (t :social/pending)
                                    #_(if issuer_id
                                        (t :badge/pendingreceived)
                                        (t :badge/pendinggiven)
                                        )])]
                                ]
                               [:div.media-left.media-top.list-item-body
                                [:img.main-img.media-object {:src (str "/" image_file)}]
                                ]
                               [:div.media-body
                                [:h4.media-heading.badge-name  name]
                                [:div.media
                                 [:div.child-profile [:div.media-left.media-top
                                                      [:img.media-object.small-img {:src (profile-picture profile_picture)}]]
                                  [:div.media-body
                                   [:p endorser]]
                                  ]]]]]]]))) [:div] endorsements)]]]))

(defn order-opts []
  [{:value "mtime" :id "radio-date" :label (t :badge/bydate)}
   {:value "name" :id "radio-name" :label (t :core/byname)}
   {:value "user" :id "radio-issuer" :label (t :badge/byuser)}])

(defn user-endorsements-content [state]
  [:div
   [m/modal-window]
   [:div#badge-stats
    (if (or (seq @(cursor state [:received]) ) (seq @(cursor state [:given])))
      [:div
       [:div.form-horizontal {:id "grid-filter"}
        [g/grid-search-field (t :core/Search ":")  "endorsementsearch" (t :badge/Filterbybadgenameoruser) :search state]
        [:div.form-group.wishlist-buttons
         [:legend {:class "control-label col-sm-2"} (str (t :core/Show) ":")]
         [:div.col-md-10
          [:div.buttons
           [:button {:class (str "btn btn-default " (when (= "all" @(cursor state [:show])) "btn-active"))
                     :id "btn-all"
                     :on-click #(do (swap! state assoc :show "all"))}
            (t :core/All)]
           [:button {:class (str "btn btn-default " (when (= "received" @(cursor state [:show])) "btn-active"))
                     :id "btn-all"
                     :on-click #(do (swap! state assoc :show "received"))}
            (t :badge/Endorsedme)]
           [:button {:class (str "btn btn-default " (when (= "given" @(cursor state [:show])) "btn-active"))
                     :id "btn-all"
                     :on-click #(do (swap! state assoc :show "given"))}
            (t :badge/Iendorsed)]]] ]
        [g/grid-radio-buttons (t :core/Order ":") "order" (order-opts) :order state]]

       (endorsements state)]
      [:div (t :badge/Youhavenoendorsements)])]])

(defn request-endorsement [])


(defn handler [site-navi]
  (let [state (atom {:initializing true
                     :permission "initial"
                     :order :mtime})
        user (session/get :user)]
    (init-user-endorsements state)
    (fn []
      (cond
        (= "initial" (:permission @state)) [:div]
        (and user (= "error" (:permission @state))) (layout/default-no-sidebar site-navi (err/error-content))
        (= "error" (:permission @state)) (layout/landing-page site-navi (err/error-content))
        :else (layout/default site-navi (user-endorsements-content state)))
      )))
