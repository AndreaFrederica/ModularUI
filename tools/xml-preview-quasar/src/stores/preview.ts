import { defineStore } from 'pinia';

export type Workspace = 'elements' | 'sources';

type PreviewState = {
  workspace: Workspace;
  screen: string;
  pickMode: boolean;
  moveMode: boolean;
  consoleOpen: boolean;
  fitViewport: boolean;
  selectedNode: string;
  leftPanel: number;
  rightPanel: number;
};

const initialState = (): PreviewState => ({
  workspace: 'elements',
  screen: 'screens/showcase.xml',
  pickMode: false,
  moveMode: false,
  consoleOpen: false,
  fitViewport: true,
  selectedNode: '',
  leftPanel: 270,
  rightPanel: 340,
});

export const usePreviewStore = defineStore('preview', {
  state: initialState,
  actions: {
    setWorkspace(workspace: Workspace) {
      this.workspace = workspace;
    },
    togglePick() {
      this.pickMode = !this.pickMode;
      if (this.pickMode) this.moveMode = false;
    },
    toggleMove() {
      this.moveMode = !this.moveMode;
      if (this.moveMode) this.pickMode = false;
    },
  },
});
