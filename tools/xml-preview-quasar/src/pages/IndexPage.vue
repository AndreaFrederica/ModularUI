<template>
  <q-page class="preview-page">
    <header class="preview-header">
      <strong>MUI XML Preview</strong>
      <q-tabs v-model="store.workspace" dense inline-label active-color="light-blue-3">
        <q-tab name="elements" label="Elements" />
        <q-tab name="sources" label="Sources" />
      </q-tabs>
      <q-space />
      <span class="connection">● local resource server</span>
    </header>
    <q-toolbar class="preview-toolbar">
      <q-btn flat dense icon="ads_click" :color="store.pickMode ? 'light-blue-3' : undefined" @click="store.togglePick" />
      <q-select v-model="store.screen" dense outlined options-dense :options="screens" label="Screen" />
      <q-space />
      <q-btn flat dense icon="open_with" label="Move window" :color="store.moveMode ? 'light-blue-3' : undefined" @click="store.toggleMove" />
      <q-btn flat dense icon="terminal" label="Console" @click="store.consoleOpen = !store.consoleOpen" />
    </q-toolbar>
    <div class="preview-workspace">
      <aside class="elements-pane">
        <div class="pane-title">DOM TREE <span>ready to migrate</span></div>
        <q-input v-model="search" dense borderless placeholder="Find element" />
        <q-tree :nodes="nodes" node-key="id" default-expand-all dark text-color="grey-3" />
      </aside>
      <section class="preview-pane">
        <div class="pane-title">Preview <span>{{ store.screen }}</span></div>
        <div class="preview-placeholder">
          <q-icon name="dashboard_customize" size="42px" color="light-blue-3" />
          <div>Quasar workbench shell</div>
          <small>Renderer migration keeps the existing XML engine compatible.</small>
        </div>
      </section>
      <aside class="inspector-pane">
        <q-tabs dense align="left" active-color="light-blue-3"><q-tab name="styles" label="Styles" /><q-tab name="computed" label="Computed" /></q-tabs>
        <div class="inspector-copy">Select an element to inspect matched rules and computed layout.</div>
      </aside>
    </div>
    <q-banner v-if="store.consoleOpen" class="console-drawer" dense>Console drawer ready for renderer events.</q-banner>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { usePreviewStore } from '../stores/preview';

const store = usePreviewStore();
const search = ref('');
const screens = ['screens/showcase.xml', 'screens/chest.xml', 'screens/chest-256.xml', 'screens/furnace.xml'];
const nodes = ref([
  { id: 'root', label: '<mui:container>', children: [{ id: 'screen', label: '<mui:screen>' }, { id: 'slots', label: '<mui:grid>' }] },
]);
</script>

<style scoped lang="scss">
.preview-page { display: flex; flex-direction: column; min-height: 100vh; background: #161b21; color: #c9d1d9; font: 12px Consolas, monospace; }
.preview-header, .preview-toolbar { background: #252a30; border-bottom: 1px solid #3a4048; }
.preview-header { display: flex; align-items: center; height: 38px; padding: 0 12px; gap: 18px; }
.preview-header strong { color: #e6edf3; }
.connection { color: #80c9aa; }
.preview-toolbar { min-height: 42px; }
.preview-workspace { display: grid; grid-template-columns: 270px minmax(0, 1fr) 340px; flex: 1; min-height: 0; }
.elements-pane, .inspector-pane { background: #20252b; overflow: auto; }
.elements-pane { border-right: 1px solid #3a4048; }
.inspector-pane { border-left: 1px solid #3a4048; }
.preview-pane { min-width: 0; background: #11161d; }
.pane-title { display: flex; justify-content: space-between; padding: 9px 12px; border-bottom: 1px solid #3a4048; color: #9eabb7; }
.pane-title span { color: #6d8192; }
.preview-placeholder { display: grid; place-items: center; align-content: center; gap: 10px; height: 100%; color: #a9b8c7; }
.preview-placeholder small, .inspector-copy { color: #8497a7; }
.inspector-copy { padding: 18px; }
.console-drawer { border-top: 1px solid #3a4048; background: #1d2228; color: #a8d1b8; }
</style>
