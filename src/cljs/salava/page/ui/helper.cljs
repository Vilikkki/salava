(ns salava.page.ui.helper
  (:require [reagent.core :refer [create-class atom cursor]]
            [reagent-modals.modals :as m]
            [markdown.core :refer [md->html]]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.core.i18n :refer [t]]
            [salava.core.ui.helper :refer [navigate-to path-for hyperlink url? plugin-fun]]
            [salava.badge.ui.helper :as bh]
            [salava.badge.ui.modal :as bm]
            [salava.core.time :refer [date-from-unix-time]]
            [salava.file.icons :refer [file-icon]]
            [salava.core.helper :refer [dump]]
            [salava.admin.ui.reporttool :refer [reporttool1]]
            [salava.core.ui.modal :refer [open-modal]]
            [clojure.string :refer [blank? starts-with?]]
            [reagent.session :as session]
            [salava.core.ui.badge-grid :refer [badge-grid-element]]
            [cljs-uuid-utils.core :refer [make-random-uuid uuid-string]]))


(defn random-key []
  (-> (make-random-uuid)
      (uuid-string)))

(defn delete-page [id]
  (ajax/DELETE
    (path-for (str "/obpv1/page/" id))
    {:handler (fn [] (do
                       (m/close-modal!)
                       (navigate-to "/profile/page")))}))

(defn delete-page-modal [page-id state]
  [:div
   [:div.modal-header
    [:button {:type "button"
              :class "close"
              :data-dismiss "modal"
              :aria-label "OK"}
     [:span {:aria-hidden "true"
             :dangerouslySetInnerHTML {:__html "&times;"}}]]]
   [:div.modal-body
    [:div {:class (str "alert alert-warning")}
     (if @(cursor state [:profile-tab?])
      (t :page/Profiletabwarning)
      (t :page/Deleteconfirm))]]
   [:div.modal-footer
    [:button {:type "button"
              :class "btn btn-primary"
              :data-dismiss "modal"}
     (t :page/Cancel)]
    [:button {:type "button"
              :class "btn btn-warning"
              :on-click #(delete-page page-id)}
     (t :page/Delete)]]])

(defn badge-block [{:keys [format image_file name description issuer_image issued_on issuer_contact criteria_url criteria_markdown issuer_content_id issuer_content_name issuer_content_url issuer_email issuer_description criteria_content creator_content_id creator_name creator_url creator_email creator_image creator_description show_evidence evidence_url evidences]}]
  [:div {:class "row badge-block badge-info flip"}
   [:div {:class "col-md-4 badge-image"}
    [:img {:src (str "/" image_file)}]]
   [:div {:class "col-md-8"}
    [:div.row
     [:div.col-md-12
      [:h3.badge-name name]]]
    [:div.row
     [:div.col-md-12
      (bh/issued-on issued_on)]]
    [:div.row
     [:div.col-md-12
      (bm/issuer-modal-link issuer_content_id issuer_content_name)
      (bm/creator-modal-link creator_content_id creator_name)]]


    [:div.row
     [:div {:class "col-md-12 description"} description]]
    [:div.row
     [:div.col-md-12
      [:h3.criteria (t :badge/Criteria)]]]
    [:div.row
     [:div.col-md-12
      [:a {:href criteria_url :target "_blank"} (t :badge/Opencriteriapage)]]]
    (if (= format "long")
      [:div
       [:div {:class "row criteria-html"}
        [:div.col-md-12
         {:dangerouslySetInnerHTML {:__html criteria_content}}]]
       [:div.row
        [:div {:class                   "col-md-12"
               :dangerouslySetInnerHTML {:__html (md->html criteria_markdown)}}]]])

    (when (seq evidences)
      [:div.row {:id "badge-settings"}
       [:div.col-md-12
        [:h3.criteria (t :badge/Evidences)]
        (reduce (fn [r evidence]
                  (let [{:keys [narrative description name id url mtime ctime properties]} evidence
                        added-by-user? (and (not (blank? description)) (starts-with? description "Added by badge recipient")) ;;use regex
                        {:keys [resource_id resource_type mime_type hidden]} properties
                        desc (cond
                               (not (blank? narrative)) narrative
                               (not added-by-user?) description ;;todo use regex to match description
                               :else nil)

                        icon-fn (first (plugin-fun (session/get :plugins) "evidence" "evidence_icon"))]
                    (conj r (when (and (not hidden) (url? url))
                              [:div.modal-evidence
                               (when-not added-by-user? [:span.label.label-success (t :badge/Verifiedevidence)])
                               [icon-fn {:type resource_type :mime_type mime_type}]
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
                                                              (open-modal [:page :view] {:page-id resource_id}))} url]
                                            (hyperlink url))
                                   (hyperlink url))]]]))))

                [:div ] evidences)]])

    #_(if (and (pos? show_evidence) evidence_url)
        [:div.row
         [:div.col-md-12
          [:h2.uppercase-header (t :badge/Evidence)]
          [:div [:a {:target "_blank" :href evidence_url} (t :badge/Openevidencepage) "..."]]]])]])

(defn html-block [{:keys [content]}]
  [:div.html-block
   (if (re-find #"iframe" (str content))
     [:div.embed-responsive.embed-responsive-16by9
      {:dangerouslySetInnerHTML {:__html (md->html content)}}]
     [:div
      {:dangerouslySetInnerHTML {:__html (md->html content)}}])])



(defn file-block [{:keys [files]}]
  [:div.file-block
   [:div.row
    [:div.col-md-12
     (if (every? #(re-find #"image/" (str (:mime_type %))) files)
       (into [:div.file-block-images]
             (for [file files]
               [:div.file-block-image
                [:img {:src (str "/" (:path file))}]]))
       [:div.file-block-attachments
        [:label.files-label
         (t :page/Attachments) ": "]
        (into [:div]
              (for [file files]
                [:span.attachment
                 [:i {:class (str "page-file-icon fa " (file-icon (:mime_type file)))}]
                 [:a.file-link {:href (str "/" (:path file))
                                :target "_blank"}
                  (:name file)]]))])]]])

(defn heading-block [{:keys [size content]}]
  [:div.heading-block
   (case size
     "h1" [:h1 content]
     "h2" [:h2 content]
     nil)])

(defn tag-block [block-atom]
 (let [{:keys [tag badges format sort]} block-atom
       container (case format
                  "short" [:div#grid {:class "row"}]
                  "long" [:div.tag-block])]

  [:div#user-badges
   [:div [:label (t :page/Tag ":")] (str " " tag)]
   [:div
     (let [sorted-badges (case sort
                           "name" (sort-by :name < badges)
                           "modified" (sort-by :mtime > badges)
                           badges)]
      (into container
       (for [badge sorted-badges]
         (if (= format "short")
          (badge-grid-element badge nil "profile" nil)
          (badge-block (assoc badge :format "long"))))))]]))

(defn showcase-block [block-atom]
 (let [{:keys [badges format title]} block-atom
        container (case format
                    "short" [:div#grid {:class "row"}]
                    "long" [:div.tag-block])]
   [:div
    [:div.heading-block
     [:h2 title]]
    [:div#user-badges
     [:div
      (doall (reduce (fn [r badge]
                       (conj r (if (= format "short")
                                   (badge-grid-element badge nil "profile" nil)
                                   (badge-block (assoc badge :format "long")))))
                     container badges))]]]))

(defn profile-block [user_id block-atom]
 (let [block (first (plugin-fun (session/get :plugins) "block" "userprofileinfo"))]
   (when block [block user_id block-atom] #_[:div {:style {:margin "10px 0px"}} [block]])))

(defn view-page [page]
  (let [{:keys [id name description mtime user_id first_name last_name blocks theme border padding visibility qr_code]} page]
    [:div {:id    (str "theme-" (or theme 0))
           :class "page-content"}
     (if id
       [:div.panel
        [:div.panel-left
         [:div.panel-right
          [:div.panel-content
           (if (and qr_code (= visibility "public"))
             [:div.row
              [:div {:class "col-xs-12 text-center"}
               [:img#print-qr-code {:src (str "data:image/png;base64," qr_code)}]]])
           (if mtime
             [:div.row
              [:div {:class "col-md-12 page-mtime"}
               (date-from-unix-time (* 1000 mtime))]])
           [:div.row
            [:div {:class "col-md-12 page-title"}
             [:h1 name]]]
           [:div.row
            [:div {:class "col-md-12 page-author"}
             [:a {:href "#" :on-click #(navigate-to (str "/profile/" user_id)) } (str first_name " " last_name)]]]
           [:div.row
            [:div {:class "col-md-12 page-summary"}
             description]]
           (into [:div.page-blocks]
                 (for [block blocks]
                   [:div {:class "block-wrapper"
                          :style {:border-top-width (:width border)
                                  :border-top-style (:style border)
                                  :border-top-color (:color border)
                                  :padding-top (str padding "px")
                                  :margin-top (str padding "px")}}
                    (case (:type block)
                      "badge" (badge-block block)
                      "html" (html-block block)
                      "file" (file-block block)
                      "heading" (heading-block block)
                      "tag" (tag-block block)
                      "showcase" (showcase-block block)
                      "profile" (profile-block user_id (atom block))
                      nil)]))]]]])]))

(defn render-page-modal [page]
  [:div {:id "badge-content"}
   [:div.modal-body
    [:div.row
     [:div.col-md-12
      [:button {:type         "button"
                :class        "close"
                :data-dismiss "modal"
                :aria-label   "OK"}
       [:span {:aria-hidden             "true"
               :dangerouslySetInnerHTML {:__html "&times;"}}]]]]]
   [view-page page]
   [:div {:class "modal-footer page-content"}
    (reporttool1 (:id page) (:name page) "page")]])



(defn view-page-modal [page]
  (create-class {:reagent-render (fn [] (render-page-modal page))
                 :component-will-unmount (fn [] (m/close-modal!))}))

(defn edit-page-header [header]
  [:div.row
   [:div.col-sm-12
    [:h1 header]]])

(defn block-specific-values [{:keys [type content badge tag format sort files title badges fields]}]
 (case type
   "heading" {:type "heading" :size "h1" :content content}
   "sub-heading" {:type "heading" :size "h2":content content}
   "badge" {:format (or format "short") :badge_id (:id badge 0)}
   "html" {:content content}
   "file" {:files (map :id files)}
   "tag" {:tag tag :format (or format "short") :sort (or sort "name")}
   "showcase" {:format (or format "short") :title (or title (t :page/Untitled)) :badges (map #(-> % (select-keys [:id :visibility])) badges)}
   "profile" {:fields fields}
   nil))

(defn prepare-blocks-to-save [blocks]
  (for [block blocks]
    (-> block
        (select-keys [:id :type])
        (merge (block-specific-values block)))))

(defn init-data [state id]
  (ajax/GET
    (path-for (str "/obpv1/page/edit/" id) true)
    {:handler (fn [data]
               (let [data-with-uuids (assoc-in data [:page :blocks] (vec (map #(assoc % :key (random-key))
                                                                              (get-in data [:page :blocks]))))]
                 (reset! state (assoc data-with-uuids :toggle-move-mode false))))}))


(defn save-page [state next-url]
  (let [{:keys [id name description blocks]} (:page @state)]
   (if (blank? name)
    (swap! state assoc :alert {:message (t :badge/Emptynamefield) :status "error"} :spinner false)
    (ajax/POST
      (path-for (str "/obpv1/page/save_content/" id))
      {:params {:name name
                :description description
                :blocks (prepare-blocks-to-save blocks)}
       :handler (fn [data]
                  (swap! state assoc :spinner false :alert nil)
                 ;(refresh-page id state)
                 (when next-url (navigate-to next-url)))}))))

(defn save-theme [state next-url]
  (let [{:keys [id theme border padding]} (:page @state)]
    (ajax/POST
      (path-for (str "/obpv1/page/save_theme/" id))
      {:params {:theme theme
                :border (:id border)
                :padding padding}
       :handler (fn [data]
                  (swap! state assoc :alert {:message (t (keyword (:message data))) :status (:status data)})
                  (js/setTimeout (fn [] (swap! state assoc :alert nil)) 3000))
       :finally (fn [] (swap! state assoc :spinner false)(when next-url (navigate-to next-url)))})))

(defn save-settings [state next-url]
  (let [{:keys [id tags visibility password name]} (:page @state)]
    (reset! (cursor state [:message]) nil)
    (ajax/POST
      (path-for (str "/obpv1/page/save_settings/" id))
      {:params {:tags tags
                :visibility visibility
                :password password}
       :handler (fn [data]
                  (swap! state assoc :alert {:message (t (keyword (:message data))) :status (:status data)})
                  (js/setTimeout (fn [] (swap! state assoc :alert nil)) 3000)
                  (if (and (= "error" (:status data)) (or (= (:message data) "profile/Profiletaberror") (= (:message data) "page/Evidenceerror")))
                    (swap! state assoc ;:message (keyword (:message data))
                           :page {:id id
                                  :tags tags
                                  :password password
                                  :visibility "public"
                                  :name name})))
       :finally (fn [] (swap! state assoc :spinner false)(when next-url (navigate-to next-url)))})))


(defn button-logic
  "Use map lookup to manage button actions. editing what a button does happens here"
  [page-id state]
  (let [urls {:content {:previous nil
                        :current (str "/profile/page/edit/" page-id)
                        :next (str "/profile/page/edit_theme/" page-id)
                        :save-function (fn [next-url] (save-page state next-url))}
              :theme {:previous (str "/profile/page/edit/" page-id)
                      :current (str "/profile/page/edit_theme/" page-id)
                      :next (str "/profile/page/settings/" page-id)
                      :save-function (fn [next-url] (save-theme state next-url))}
              :settings {:previous (str "/profile/page/edit_theme/" page-id)
                         :current (str "/profile/page/settings/" page-id)
                         :next (str "/profile/page/preview/" page-id)
                         :save-function (fn [next-url] (save-settings state next-url))}

              :preview {:previous (str "/profile/page/settings/" page-id)
                        :current (str "/profile/page/preview/" page-id)
                        :next nil}}]

    {:content {:save! (get-in urls [:content :save-function])
               :save-and-next! (fn [] (save-page state (get-in urls [:content :next])))
               :url (get-in urls [:content :current])
               :go! (fn [] (navigate-to (get-in urls [:content :current])))
               :editable? true
               :previous false
               :next true}
     :theme {:save! (get-in urls [:theme :save-function])
             :save-and-next! (fn [] (save-theme state (get-in urls [:theme :next])))
             :save-and-previous! (fn [] (save-theme state (get-in urls [:theme :previous])))
             :go! (fn [] (navigate-to (get-in urls [:theme :current])))
             :editable? true
             :url (get-in urls [:theme :current])
             :previous true
             :next true}

     :settings {:save! (get-in urls [:settings :save-function])
                :save-and-next! (fn [] (save-settings state (get-in urls [:settings :next])))
                :save-and-previous! (fn [] (save-settings state (get-in urls [:settings :previous])))
                :go! (fn [] (navigate-to (get-in urls [:settings :current])))
                :editable? true
                :url (get-in urls [:settings :current])
                :previous true
                :next true}

     :preview {:save! #()
               :go! (fn [] (navigate-to (get-in urls [:preview :current])))
               :editable? false
               :url (get-in urls [:preview :current])
               :previous true
               :next false}}))

(defn edit-page-buttons [id target state]
  (let [logic (button-logic id state)
        editable? (get-in logic [target :editable?])]

     [:div {:class "row flip"
                               :id "buttons"}
      [:div.col-md-12 {:style {:width "100%"}}

                 [:div {;:class "col-md-3 col-sm-3 col-xs-12 col-sm-push-9"
                        :id "buttons-right"}
                  [:button.btn.btn-danger {:on-click #(do
                                                         (.preventDefault %)
                                                         (m/modal! (delete-page-modal id state)))}
                     [:i.fa.fa-trash.fa-fw.fa-lg](t :core/Delete)]]
                 [:div.wizard ;{:style {:width "75%"}};.col-md-9.col-sm-9.col-xs-12.col-sm-pull-3.wizard
                  [:a {:class (if (= target :content) "current")
                       :href "#"
                       :on-click #(do
                                    (.preventDefault %)
                                    (if editable?
                                      (as-> (get-in logic [target :save!]) f (f (get-in logic [:content :url])))
                                      (as-> (get-in logic [:content :go!])  f (f))))}
                   [:span {:class (str "badge" (if (= target :content) " badge-inverse" ))} "1."]
                   (t :page/Content)]
                  [:a {:class (if (= target :theme) "current")
                       :href "#"
                       :on-click #(do (.preventDefault %)
                                    (if editable?
                                      (as-> (get-in logic [target :save!]) f (f (get-in logic [:theme :url])))
                                      (as-> (get-in logic [:theme :go!]) f (f))))}
                   [:span {:class (str "badge" (if (= target :theme) " badge-inverse" ))} "2."]
                   (t :page/Theme)]
                  [:a {:class (if (= target :settings) "current")
                       :href "#"
                       :on-click #(do (.preventDefault %)
                                    (if editable?
                                      (as-> (get-in logic [target :save!]) f (f (get-in logic [:settings :url])))
                                      (as-> (get-in logic [:settings :go!]) f (f))))}
                   [:span {:class (str "badge" (if (= target :settings) " badge-inverse" ))} "3."]
                   (t :page/Settings)]
                  [:a {:class (if (= target :preview) "current")
                       :href "#"
                       :on-click #(do (.preventDefault %)
                                    (if editable?
                                      (as-> (get-in logic [target :save!]) f (f (get-in logic [:preview :url])))
                                      (as-> (get-in logic [:preview :go!]) f (f))))}

                   [:span {:class (str "badge" (if (= target :preview) " badge-inverse" ))} "4."]
                   (t  :page/Preview)]]]


      [m/modal-window]]))

(defn manage-page-buttons [current id state]
  (let [id @id
        logic (button-logic id state)
        previous? (get-in logic [current :previous])
        next?  (get-in logic [current :next])]

   (create-class {:reagent-render   (fn []
                                      [:div.action-bar {:id "page-edit"}
                                       [:div.row
                                        [:div.col-md-12
                                         (when previous? [:a {:href "#"
                                                              :on-click #(do
                                                                          (.preventDefault %)
                                                                          (as-> (get-in logic [current :save-and-previous!]) f (f)))}
                                                          [:div {:id "step-button-previous"}
                                                                (t :core/Previous)]])
                                         [:button {:class    "btn btn-primary"
                                                   :on-click #(do
                                                                (.preventDefault %)
                                                                (swap! state assoc :spinner true)
                                                                (js/setTimeout (fn [] (as-> (get-in logic [current :save!]) f (f))) 2000))}

                                          (when (:spinner @state) [:i.fa.fa-spinner.fa-spin.fa-lg {:style {:padding "0 3px"}}]) (t :page/Save)]
                                         [:button.btn.btn-primary {:on-click #(do
                                                                               (.preventDefault %)
                                                                               (navigate-to (str "/profile/page/view/" id)))}
                                          [:i.fa.fa-eye.fa-fw.fa-lg](t :page/View)]
                                         [:button.btn.btn-warning {:on-click #(do
                                                                                (.preventDefault %)
                                                                                (navigate-to  "/profile/page"))}
                                          (t :core/Cancel)]
                                         (when next?  [:a {:href "#"
                                                           :on-click #(do
                                                                       (.preventDefault %)
                                                                       (as-> (get-in logic [current :save-and-next!]) f (f)))}
                                                          [:div.pull-right {:id "step-button"}
                                                                           (t :core/Next)]])]]

                                       (when (and (= "error" (get-in @state [:alert :status])) (not= current :settings))
                                         [:div.row
                                          [:div.col-md-12
                                           [:div {:class (str "alert " (case (get-in @state [:alert :status])
                                                                         "success" "alert-success"
                                                                         "error" "alert-warning"))
                                                  :style {:display "block" :margin-bottom "20px"}}
                                            (get-in @state [:alert :message] nil)]]])])})))
