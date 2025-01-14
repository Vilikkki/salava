(ns salava.profile.schemas
  (:require [schema.core :as s
             :include-macros true]
            [salava.core.schema-helper :as h]))

(def additional-fields
  [{:type "email" :key :user/Emailaddress}
   {:type "phone" :key :user/Phonenumber}
   {:type "address" :key :user/Address}
   {:type "city" :key :user/City}
   {:type "state" :key :user/State}
   {:type "country" :key :user/Country}
   {:type "facebook" :key :user/Facebookaccount}
   {:type "linkedin" :key :user/LinkedInaccount}
   {:type "twitter" :key :user/Twitteraccount}
   {:type "pinterest" :key :user/Pinterestaccount}
   {:type "instagram" :key :user/Instagramaccount}
   {:type "blog" :key :user/Blog}])

(s/defschema User {:email      (s/constrained s/Str #(and (>= (count %) 1)
                                                          (<= (count %) 255)
                                                          (h/email-address? %)))
                   :first_name (s/constrained s/Str #(and (>= (count %) 1)
                                                          (<= (count %) 255)))
                   :last_name  (s/constrained s/Str #(and (>= (count %) 1)
                                                          (<= (count %) 255)))
                   :profile_visibility (s/enum "public" "internal")
                   :profile_picture (s/maybe s/Str)
                   :about (s/maybe s/Str)})

(s/defschema Badge {:id s/Int
                    :image_file (s/maybe s/Str)
                    :description (s/maybe s/Str)
                    :name s/Str})

(s/defschema ShowcaseBlock {:type (s/eq "showcase")
                            :title  (s/maybe s/Str)
                            :badges [{:id (s/maybe s/Int) (s/optional-key :visibility) s/Str}]
                            :format (s/enum "short" "medium" "long")})

(s/defschema BlockForEdit (s/conditional
                                   #(= (:type %) "showcase") (assoc ShowcaseBlock (s/optional-key :id) s/Int
                                                              (s/optional-key :block_order) s/Int)
                                                              ;:badges [(s/maybe s/Int)])

                                   #(= (:type %) "badges") {:block_order s/Int :type (s/eq "badges") :hidden (s/maybe s/Bool)}
                                   #(= (:type %) "pages") {:block_order s/Int :type (s/eq "pages") :hidden (s/maybe s/Bool)}
                                   #(= (:type %) "location") {:block_order s/Int :type (s/eq "location") (s/optional-key :hidden) (s/maybe s/Bool)}))

(s/defschema EditProfile (assoc (-> User (select-keys [:profile_visibility :profile_picture :about]))
                                :fields [{:field (apply s/enum (map :type additional-fields)) :value (s/maybe s/Str)}]
                                :blocks [(s/maybe BlockForEdit)]
                                :theme (s/maybe s/Int)
                                :tabs  [(s/maybe {:id s/Int :name s/Str :visibility s/Str})]))
