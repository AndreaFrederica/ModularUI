package com.cleanroommc.modularui.screen.dom;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.component.MuiElementDescriptor;
import com.cleanroommc.modularui.api.component.MuiElementRegistry;
import com.cleanroommc.modularui.api.component.MuiPropertyDescriptor;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.sync.MuiProtocolInstallation;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CategoryList;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.FluidDisplayWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.ScrollingTextWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.SortButtons;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.cleanroommc.modularui.widgets.textfield.TextEditorWidget;
import com.cleanroommc.modularui.widgets.textfield.CodeEditorWidget;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
final class NativeElementLibrary {

    private NativeElementLibrary() {}

    static MuiElementRegistry createDefaultRegistry(ModularScreen screen) {
        MuiElementRegistry registry = new MuiElementRegistry();
        registry.register(MuiElementDescriptor.builder("mui:container", ParentWidget.class,
                        element -> new ParentWidget<>())
                .childModel(MuiElementDescriptor.ChildModel.MULTIPLE)
                .build());
        registry.register(MuiElementDescriptor.builder("mui:scroll", ScrollWidget.class,
                        element -> new ScrollWidget<>())
                .childModel(MuiElementDescriptor.ChildModel.MULTIPLE)
                .build());
        registry.register(MuiElementDescriptor.builder("mui:list", ListWidget.class,
                        element -> new ListWidget<>())
                .childModel(MuiElementDescriptor.ChildModel.MULTIPLE)
                .build());
        registry.register(MuiElementDescriptor.builder("mui:row", Flow.class,
                        element -> new Flow(GuiAxis.X))
                .childModel(MuiElementDescriptor.ChildModel.MULTIPLE)
                .build());
        registry.register(MuiElementDescriptor.builder("mui:column", Flow.class,
                        element -> new Flow(GuiAxis.Y))
                .childModel(MuiElementDescriptor.ChildModel.MULTIPLE)
                .build());
        registry.register(MuiElementDescriptor.builder("mui:grid", Grid.class, element -> new Grid())
                .childModel(MuiElementDescriptor.ChildModel.GRID)
                .property(new MuiPropertyDescriptor<>("columns", NativeElementLibrary::positiveInt,
                        Grid::setDomColumns))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:button", ButtonWidget.class,
                        element -> new ButtonWidget<>())
                .childModel(MuiElementDescriptor.ChildModel.SINGLE)
                .property(new MuiPropertyDescriptor<>("play-click-sound", value -> bool(value, true),
                        ButtonWidget::playClickSound))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:text", TextWidget.class,
                        element -> new TextWidget<>(IKey.dynamic(() -> {
                            String text = element.getAttribute("text");
                            return text == null ? element.getTextContent() : text;
                        })))
                .childModel(MuiElementDescriptor.ChildModel.NONE)
                .property(new MuiPropertyDescriptor<>("scale", value -> floatValue(value, 1f),
                        TextWidget::scale))
                .property(new MuiPropertyDescriptor<>("shadow", value -> nullableBool(value),
                        TextWidget::shadow))
                .property(new MuiPropertyDescriptor<>("max-width", value -> intValue(value, -1),
                        TextWidget::maxWidth))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:progress", ProgressWidget.class,
                        element -> bind(new ProgressWidget(), element))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:item-slot", ItemSlot.class,
                        element -> boundItemSlot(screen, element))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:fluid-slot", FluidSlot.class,
                        element -> bind(new FluidSlot(), element))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:item-display", ItemDisplayWidget.class,
                        element -> bind(new ItemDisplayWidget(), element))
                .property(new MuiPropertyDescriptor<>("display-amount", value -> bool(value, false),
                        ItemDisplayWidget::displayAmount))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:slider", SliderWidget.class,
                        element -> bind(new SliderWidget(), element))
                .property(new MuiPropertyDescriptor<>("min", value -> doubleValue(value, 0),
                        (widget, value) -> widget.bounds(value, widget.getMax())))
                .property(new MuiPropertyDescriptor<>("max", value -> doubleValue(value, 100),
                        (widget, value) -> widget.bounds(widget.getMin(), value)))
                .property(new MuiPropertyDescriptor<>("step", value -> doubleValue(value, 0),
                        (widget, value) -> { if (value > 0) widget.stopper(value); }))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:toggle", ToggleButton.class,
                        element -> bind(new ToggleButton(), element))
                .property(new MuiPropertyDescriptor<>("invert", value -> bool(value, false),
                        ToggleButton::invertSelected))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:text-field", TextFieldWidget.class,
                        element -> bind(new TextFieldWidget(), element))
                .property(new MuiPropertyDescriptor<>("max-length", value -> positiveInt(value),
                        TextFieldWidget::setMaxLength))
                .property(new MuiPropertyDescriptor<>("update-on-change", value -> bool(value, false),
                        TextFieldWidget::autoUpdateOnChange))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:scrolling-text", ScrollingTextWidget.class,
                        element -> new ScrollingTextWidget(IKey.dynamic(() -> text(element))))
                .property(new MuiPropertyDescriptor<>("speed", value -> positiveInt(value),
                        ScrollingTextWidget::scrollSpeed))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:cycle-button", CycleButtonWidget.class,
                        element -> bind(new CycleButtonWidget(), element))
                .childModel(MuiElementDescriptor.ChildModel.SINGLE)
                .property(new MuiPropertyDescriptor<>("states", value -> positiveInt(value),
                        CycleButtonWidget::stateCount))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:fluid-display", FluidDisplayWidget.class,
                        element -> bind(new FluidDisplayWidget(), element))
                .property(new MuiPropertyDescriptor<>("capacity", value -> intValue(value, 0),
                        FluidDisplayWidget::capacity))
                .property(new MuiPropertyDescriptor<>("display-amount", value -> bool(value, true),
                        FluidDisplayWidget::displayAmount))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:slot-group", SlotGroupWidget.class,
                        element -> new SlotGroupWidget().disableSortButtons())
                .childModel(MuiElementDescriptor.ChildModel.MULTIPLE)
                .property(new MuiPropertyDescriptor<>("slot-group", NativeElementLibrary::requiredText,
                        SlotGroupWidget::slotGroup))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:sort-buttons", SortButtons.class,
                        element -> new SortButtons())
                .property(new MuiPropertyDescriptor<>("slot-group", NativeElementLibrary::requiredText,
                        SortButtons::slotGroup))
                .property(new MuiPropertyDescriptor<>("vertical", value -> bool(value, false),
                        (widget, value) -> { if (value) widget.vertical(); else widget.horizontal(); }))
                .build());
        registry.register(MuiElementDescriptor.builder("mui:category-list", CategoryList.class,
                        element -> new CategoryList())
                .childModel(MuiElementDescriptor.ChildModel.MULTIPLE)
                .build());
        registry.register(MuiElementDescriptor.builder("mui:text-editor", TextEditorWidget.class,
                        element -> new TextEditorWidget())
                .build());
        registry.register(MuiElementDescriptor.builder("mui:code-editor", CodeEditorWidget.class,
                        element -> new CodeEditorWidget())
                .build());
        return registry;
    }

    private static String text(com.cleanroommc.modularui.api.dom.MuiElement element) {
        String value = element.getAttribute("text");
        return value == null ? element.getTextContent() : value;
    }

    private static String requiredText(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Expected non-empty text");
        return value.trim();
    }

    private static ItemSlot boundItemSlot(ModularScreen screen, com.cleanroommc.modularui.api.dom.MuiElement element) {
        String key = requiredBind(element);
        int id = bindId(element);
        MuiProtocolInstallation installation = screen.getContext().getUISettings().getProtocolInstallation();
        SyncHandler handler = installation == null
                ? screen.getSyncManager().findSyncHandlerNullable(key, id)
                : installation.getSlot(key, id);
        if (!(handler instanceof ItemSlotSH slotHandler)) {
            throw new IllegalStateException("XML item slot binding '" + key + ':' + id
                    + "' is not an installed ItemSlot handler");
        }
        return new ItemSlot().syncHandler(slotHandler);
    }

    private static <W extends com.cleanroommc.modularui.widget.Widget<W>> W bind(
            W widget, com.cleanroommc.modularui.api.dom.MuiElement element) {
        String key = element.getAttribute("bind");
        if (key != null && !key.trim().isEmpty()) widget.syncHandler(key.trim(), bindId(element));
        return widget;
    }

    private static String requiredBind(com.cleanroommc.modularui.api.dom.MuiElement element) {
        String key = element.getAttribute("bind");
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException(element.getTagName() + " requires a non-empty bind attribute");
        }
        return key.trim();
    }

    private static int bindId(com.cleanroommc.modularui.api.dom.MuiElement element) {
        return intValue(element.getAttribute("bind-id"), 0);
    }

    static boolean bool(String value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Expected true or false");
    }

    private static double doubleValue(String value, double defaultValue) {
        return value == null ? defaultValue : Double.parseDouble(value.trim());
    }

    static Boolean nullableBool(String value) {
        return value == null ? null : bool(value, false);
    }

    static int intValue(String value, int defaultValue) {
        if (value == null) return defaultValue;
        return Integer.parseInt(value.trim());
    }

    static int positiveInt(String value) {
        int parsed = intValue(value, 1);
        if (parsed <= 0) throw new IllegalArgumentException("Expected a positive integer");
        return parsed;
    }

    static float floatValue(String value, float defaultValue) {
        if (value == null) return defaultValue;
        float parsed = Float.parseFloat(value.trim());
        if (!Float.isFinite(parsed)) throw new IllegalArgumentException("Expected a finite number");
        return parsed;
    }
}
