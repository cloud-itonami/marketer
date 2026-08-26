(ns marketer.app-test
  (:require [cljs.test :refer [deftest is testing]]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [marketer.app :as app]))

(deftest initialize-db-handler-test
  (testing "initialize-db-handler returns the expected default db regardless of prior state/event"
    (is (= app/default-db (app/initialize-db-handler {} [:initialize-db])))
    (is (= app/default-db (app/initialize-db-handler {:title "stale"} [:initialize-db])))
    (is (= "marketer-ui-iewsbshk" (:title (app/initialize-db-handler nil nil))))
    (is (= "Vite entry scaffold after SvelteKit cleanup." (:subtitle (app/initialize-db-handler nil nil))))))

(deftest title-sub-test
  (testing "title-sub extracts :title from the db, ignoring the query vector"
    (is (= "hello" (app/title-sub {:title "hello" :subtitle "world"} [:title])))
    (is (nil? (app/title-sub {} [:title])))))

(deftest subtitle-sub-test
  (testing "subtitle-sub extracts :subtitle from the db, ignoring the query vector"
    (is (= "world" (app/subtitle-sub {:title "hello" :subtitle "world"} [:subtitle])))
    (is (nil? (app/subtitle-sub {} [:subtitle])))))

(deftest dispatch-and-subscribe-roundtrip-test
  (testing "the registered :initialize-db event and :title/:subtitle subs wire up end-to-end"
    ;; Reset re-frame's app-db so this test does not depend on ordering/state
    ;; leaked by other tests in the same process.
    (reset! rf-db/app-db {})
    (rf/dispatch-sync [:initialize-db])
    (is (= "marketer-ui-iewsbshk" @(rf/subscribe [:title])))
    (is (= "Vite entry scaffold after SvelteKit cleanup." @(rf/subscribe [:subtitle])))
    (is (= app/default-db @rf-db/app-db))))
