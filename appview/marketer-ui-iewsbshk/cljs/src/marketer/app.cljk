(ns marketer.app
  "marketer-ui-iewsbshk — appview frontend.

  Faithful cljs port of the previous Svelte scaffold (`svelte/src/App.svelte`,
  `svelte/src/main.ts`): a single static page rendering a title and a
  subtitle, no other behaviour existed to preserve. Rebuilt on this
  workspace's mandatory stack (reagent + re-frame + hiccup, jp-go-dds as the
  base design system — see CLAUDE.md / skill `kotoba-uiux`) instead of
  inventing new features.

  `default-db` / `initialize-db-handler` / `title-sub` / `subtitle-sub` are
  named (not anonymous) so `test/marketer/app_test.cljs` can assert on the
  event/sub logic directly, independent of mounting."
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [jp-go-dds.core :as dds]))

(def default-db
  {:title "marketer-ui-iewsbshk"
   :subtitle "Vite entry scaffold after SvelteKit cleanup."})

(defn initialize-db-handler
  [_db _event]
  default-db)

(defn title-sub
  [db _query-v]
  (:title db))

(defn subtitle-sub
  [db _query-v]
  (:subtitle db))

(rf/reg-event-db :initialize-db initialize-db-handler)
(rf/reg-sub :title title-sub)
(rf/reg-sub :subtitle subtitle-sub)

(defn main-panel []
  (let [title (rf/subscribe [:title])
        subtitle (rf/subscribe [:subtitle])]
    (fn []
      [dds/container
       [:div {:class "dds-ext-hero dds-ext-center"}
        [dds/heading 1 @title]
        [:p {:class "dds-ext-lead"} @subtitle]]])))

(defn mount-root []
  (rdom/render [main-panel] (.getElementById js/document "app")))

(defn main
  "shadow-cljs :init-fn — dispatch the initial db then mount."
  []
  (rf/dispatch-sync [:initialize-db])
  (mount-root))
