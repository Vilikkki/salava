(ns salava.page.ui.my
  (:require [reagent.core :refer [atom create-class]]
            [clojure.set :refer [intersection]]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.core.ui.helper :as h :refer [unique-values navigate-to path-for not-activated?]]
            [salava.core.ui.notactivated :refer [not-activated-banner]]
            [salava.core.ui.layout :as layout]
            [salava.core.ui.grid :as g]
            [salava.core.i18n :refer [t]]
            [salava.core.time :refer [date-from-unix-time]]
            [salava.core.helper :refer [dump]]
            [salava.core.ui.page-grid :refer [page-grid-element]]))

(defn visibility-select-values []
  [{:value "all" :title (t :core/All)}
   {:value "public"  :title (t :core/Public)}
   {:value "internal"  :title (t :core/Shared)}
   {:value "private" :title (t :core/Private)}])

(defn order-radio-values []
  [{:value "mtime" :id "radio-date" :label (t :core/bydate)}
   {:value "name" :id "radio-name" :label (t :core/bypagetitle)}])

(defn create-page []
  (ajax/POST
    (path-for "/obpv1/page/create")
    {:params {:userid 1}
     :handler (fn [id]
                (navigate-to (str "/profile/page/edit/" id)))}))

(defn page-grid-form
 ([state]
  [:div {:id "grid-filter"
         :class "form-horizontal"}
   [g/grid-search-field  (t :core/Search ":") "pagesearch" (t :core/Searchbyname) :search state]
   [g/grid-select        (t :core/Show ":")  "select-visibility" :visibility (visibility-select-values) state]
   [g/grid-buttons       (t :core/Tags ":") (unique-values :tags (:pages @state)) :tags-selected :tags-all state]
   [g/grid-radio-buttons (t :core/Order ":")  "order" (order-radio-values) :order state]])
 ([state modal?]
  [:form {:id "grid-filter"
          :class "form-horizontal"}
   [g/grid-search-field  (t :core/Search ":") "pagesearch" (t :core/Searchbyname) :search state]
   [g/grid-select        (t :core/Show ":")  "select-visibility" :visibility (visibility-select-values) state]
   [g/grid-buttons       (t :core/Tags ":") (unique-values :tags (:pages @state)) :tags-selected :tags-all state]
   [g/grid-radio-buttons (t :core/Order ":")  "order" (order-radio-values) :order state]]))

(defn page-visible? [element state]
  (if (and
        (or (= (:visibility @state) "all")
            (= (:visibility @state) (:visibility element)))
        (or (> (count
                 (intersection
                   (into #{} (:tags-selected @state))
                   (into #{} (:tags element))))
               0)
            (= (:tags-all @state)
               true))
        (or (empty? (:search @state))
            (not= (.indexOf
                    (.toLowerCase (:name element))
                    (.toLowerCase (:search @state)))
                  -1)))
    true false))

(defn page-grid [state]
  (let [pages (:pages @state)
        order (keyword (:order @state))
        pages (if (= order :mtime)
                (sort-by order > pages)
                (sort-by (comp clojure.string/upper-case order) pages))]
    (if (not-activated?)
      (not-activated-banner)
      [:div {:class "row wrap-grid"
             :id    "grid"}
       [:div {:class "col-xs-12 col-sm-6 col-md-4"
              :id    "add-element"
              :key   "new-page"}
        [:div {:class "media grid-container"}
         [:a {:class    "add-element-link"
              :href     "#"
              :on-click #(create-page)}
          [:div.media-content
           [:div.media-body
            [:div {:id "add-element-icon"}
             [:i {:class "fa fa-plus"}]]
            [:div {:id "add-element-link"}
             (t :page/Addpage)]]]]]]
       (doall
        (for [element-data pages]
          (if (page-visible? element-data state)
            (page-grid-element element-data {:type "basic"}))))])))

(defn content [state]
  [:div {:class "my-badges pages"}
   [page-grid-form state]
   [page-grid state]])

(defn init-data [state]
  (ajax/GET
    (path-for "/obpv1/page" true)
    {:handler (fn [data]
                (swap! state assoc :pages data))}))

(defn handler [site-navi]
  (let [state (atom {:pages []
                     :visibility "all"
                     :order "mtime"
                     :tags-all true
                     :tags-selected []})]
    (init-data state)
    (fn []
      (layout/default site-navi (content state)))))

(defn mypagesmodal [param]
  (let [state (atom {:pages []
                     :visibility "all"
                     :order "mtime"
                     :tags-all true
                     :tags-selected []})
        tab-atom (:tab-atom param)]
   (fn []
    (create-class {:reagent-render (fn []
                                      (let [pages (:pages @state)
                                            order (keyword (:order @state))
                                            pages (if (= order :mtime)
                                                    (sort-by order > pages)
                                                    (sort-by (comp clojure.string/upper-case order) pages))]

                                       (if (not-activated?)
                                         (not-activated-banner)
                                        [:div {:class "my-badges pages" :style {:padding "15px"}}
                                              [page-grid-form state true]
                                              [:div {:class "row wrap-grid"
                                                     :id    "grid"}
                                               (when (empty? pages) [:div {:class "col-xs-12 col-sm-6 col-md-4"
                                                                           :id    "add-element"
                                                                           :key   "new-page"}
                                                                     [:div {:class "media grid-container"}
                                                                      [:a {:class    "add-element-link"
                                                                           :href     "#"
                                                                           :on-click #(create-page)}
                                                                       [:div.media-content
                                                                        [:div.media-body
                                                                         [:div {:id "add-element-icon"}
                                                                          [:i {:class "fa fa-plus" :style {:font-size "70px"}}]]
                                                                         [:div {:id "add-element-link"}
                                                                          (t :page/Addpage)]]]]]])

                                               (doall
                                                (for [element-data pages]
                                                  (if (page-visible? element-data state)
                                                    (page-grid-element (assoc element-data :atom tab-atom) {:type "pickable"}))))]])))
                   :component-will-mount (fn [] (init-data state))}))))
