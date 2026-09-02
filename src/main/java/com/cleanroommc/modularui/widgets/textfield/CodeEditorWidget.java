package com.cleanroommc.modularui.widgets.textfield;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.TextFieldTheme;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import java.awt.Point;

/**
 * Client-only multiline editor intended for source code and markup.
 * It keeps the normal MUI text editing semantics while using a code-friendly
 * top-left layout and independent vertical scrolling.
 */
public final class CodeEditorWidget extends TextEditorWidget {

    public CodeEditorWidget() {
        super();
        setTextAlignment(Alignment.TopLeft);
        setTextColor(0xffd7e2eb);
        setMarkedColor(0xff315d78);
        setScale(0.72f);
        padding(6);
        showScrollShadows(false);
        getScrollArea().setScrollDataY(new VerticalScrollData(false, 4));
    }

    @Override
    protected void drawText(ModularGuiContext context, TextFieldTheme widgetTheme) {
        super.drawText(context, widgetTheme);
        if (getScrollArea().getScrollY() != null) {
            int contentHeight = (int) Math.ceil(this.renderer.getLastActualHeight()
                    + getArea().getPadding().vertical());
            getScrollArea().getScrollY().setScrollSize(Math.max(contentHeight, getArea().paddedHeight()));
        }
    }

    @Override
    public @NotNull Result onKeyPressed(char character, int keyCode) {
        if (!isFocused()) return super.onKeyPressed(character, keyCode);
        Point cursor = this.handler.getMainCursor();
        if (keyCode == Keyboard.KEY_HOME) {
            int line = Interactable.hasControlDown() ? 0 : cursor.y;
            this.handler.setCursor(line, 0,
                    !Interactable.hasShiftDown(), true);
            return Result.SUCCESS;
        }
        if (keyCode == Keyboard.KEY_END) {
            int end = Interactable.hasControlDown() ? this.handler.getText().get(this.handler.getText().size() - 1).length()
                    : this.handler.getText().get(cursor.y).length();
            int line = Interactable.hasControlDown() ? this.handler.getText().size() - 1 : cursor.y;
            this.handler.setCursor(line, end, !Interactable.hasShiftDown(), true);
            return Result.SUCCESS;
        }
        if (isEditable() && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
            String indent = leadingWhitespace(this.handler.getText().get(cursor.y));
            Result result = super.onKeyPressed(character, keyCode);
            if (result == Result.SUCCESS && !indent.isEmpty()) this.handler.insert(indent, true);
            return result;
        }
        return super.onKeyPressed(character, keyCode);
    }

    private static String leadingWhitespace(String line) {
        int end = 0;
        while (end < line.length()) {
            char c = line.charAt(end);
            if (c != ' ' && c != '\t') break;
            end++;
        }
        return line.substring(0, end);
    }
}
