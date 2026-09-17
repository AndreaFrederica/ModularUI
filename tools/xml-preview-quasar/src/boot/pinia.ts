import { createPinia } from 'pinia';
import type { App } from 'vue';

export default ({ app }: { app: App }) => {
  app.use(createPinia());
};
