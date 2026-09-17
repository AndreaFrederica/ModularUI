<template><q-page class="legacy-host"><div id="legacy-root" aria-label="MUI XML Preview"></div></q-page></template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue';
let runtime: { dispose?: () => void } | undefined;
onMounted(async () => {
  const host = document.getElementById('legacy-root');
  if (!host) return;
  const source = await (await fetch('/legacy-template.html')).text();
  const template = new DOMParser().parseFromString(source, 'text/html');
  for (const node of [...template.body.childNodes]) {
    if (node.nodeType === Node.ELEMENT_NODE && ['SCRIPT', 'LINK'].includes((node as Element).tagName)) continue;
    host.append(node.cloneNode(true));
  }
  runtime = await import('../lib/legacy-app.js');
});
onBeforeUnmount(() => runtime?.dispose?.());
</script>
<style lang="scss">
@use '../css/preview.scss';
.legacy-host { min-height: 100vh; padding: 0; background: #1b1e23; }
.legacy-host > #legacy-root { height: 100vh; }
</style>
