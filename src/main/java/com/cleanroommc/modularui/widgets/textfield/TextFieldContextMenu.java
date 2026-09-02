package com.cleanroommc.modularui.widgets.textfield;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.menu.Menu;
import com.cleanroommc.modularui.widgets.menu.MenuPanel;

import net.minecraft.client.gui.GuiScreen;

/** Builds the common right-click menu for all MUI text fields. */
final class TextFieldContextMenu {

    private static final int MENU_WIDTH = 128;
    private static final int MENU_ITEM_HEIGHT = 16;
    private static final int MENU_ITEM_COUNT = 5;

    private TextFieldContextMenu() {}

    static ModularPanel create(BaseTextFieldWidget<?> field, int mouseX, int mouseY) {
        boolean hasSelection = field.handler.hasTextMarked();
        int screenWidth = field.getContext().getScreenArea().w();
        int screenHeight = field.getContext().getScreenArea().h();
        int x = Math.max(0, Math.min(mouseX, Math.max(0, screenWidth - MENU_WIDTH)));
        int y = Math.max(0, Math.min(mouseY,
                Math.max(0, screenHeight - MENU_ITEM_HEIGHT * MENU_ITEM_COUNT)));

        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> items = new ListWidget<>()
                .widthRel(1f)
                .coverChildrenHeight()
                // Keep disabled commands visible, matching native editor context menus.
                .collapseDisabledChild(false);
        addItem(items, "Copy", hasSelection, () -> {
            GuiScreen.setClipboardString(field.handler.getSelectedText());
        });
        addItem(items, "Cut", hasSelection && field.isEditable(), () -> {
            GuiScreen.setClipboardString(field.handler.getSelectedText());
            field.handler.delete();
        });
        addItem(items, "Paste", field.isEditable(), () -> {
            String clipboard = GuiScreen.getClipboardString();
            if (clipboard == null || clipboard.isEmpty()) return;
            if (field.handler.hasTextMarked()) field.handler.delete();
            field.handler.insert(clipboard.replace("§", ""), field.canScrollHorizontally());
        });
        addItem(items, "Select All", true, field.handler::markAll);
        addItem(items, "Delete", hasSelection && field.isEditable(), field.handler::delete);

        Menu<?> menu = new Menu<>()
                .width(MENU_WIDTH)
                .coverChildrenHeight()
                .child(items);
        menu.left(x).top(y);
        // MenuPanel is full-screen and invisible; its child coordinates are screen coordinates.
        return new MenuPanel("mui.text_field_context", menu);
    }

    private static void addItem(ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> items,
                                String label, boolean enabled, Runnable action) {
        ButtonWidget<?> item = new ButtonWidget<>()
                .widthRel(1f)
                .height(MENU_ITEM_HEIGHT)
                .child(IKey.str(label).asWidget().left(7))
                .onMousePressed(button -> {
                    if (button != 0 || !enabled) return false;
                    action.run();
                    if (items.getPanel() != null) items.getPanel().closeIfOpen();
                    return true;
                });
        item.setEnabled(enabled);
        items.child(item);
    }
}
