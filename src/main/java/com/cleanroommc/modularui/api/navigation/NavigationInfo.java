package com.cleanroommc.modularui.api.navigation;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/** Immutable semantic metadata attached to a widget. */
public final class NavigationInfo {

    public static final NavigationInfo NONE = builder(NavigationRole.NONE).focusable(false).build();

    @Nullable private final String id;
    private final NavigationRole role;
    private final Supplier<String> label;
    private final Set<NavigationAction> actions;
    @Nullable private final String group;
    private final int order;
    private final NavigationAxis primaryAxis;
    private final boolean focusable;
    private final boolean wrapHorizontal;
    private final boolean wrapVertical;
    private final boolean trapFocus;

    private NavigationInfo(Builder builder) {
        this.id = builder.id;
        this.role = builder.role;
        this.label = builder.label;
        this.actions = Collections.unmodifiableSet(builder.actions.clone());
        this.group = builder.group;
        this.order = builder.order;
        this.primaryAxis = builder.primaryAxis;
        this.focusable = builder.focusable;
        this.wrapHorizontal = builder.wrapHorizontal;
        this.wrapVertical = builder.wrapVertical;
        this.trapFocus = builder.trapFocus;
    }

    public static Builder builder(NavigationRole role) {
        return new Builder(role);
    }

    @Nullable
    public String getId() {
        return this.id;
    }

    public NavigationRole getRole() {
        return this.role;
    }

    public String getLabel() {
        String value = this.label.get();
        return value == null ? "" : value;
    }

    public Set<NavigationAction> getActions() {
        return this.actions;
    }

    @Nullable
    public String getGroup() {
        return this.group;
    }

    public int getOrder() {
        return this.order;
    }

    public NavigationAxis getPrimaryAxis() {
        return this.primaryAxis;
    }

    public boolean isFocusable() {
        return this.focusable;
    }

    /** Legacy all-axis query. Prefer the axis-specific accessors. */
    public boolean isWrap() { return this.wrapHorizontal && this.wrapVertical; }

    public boolean isWrapHorizontal() { return this.wrapHorizontal; }

    public boolean isWrapVertical() { return this.wrapVertical; }

    public boolean isTrapFocus() {
        return this.trapFocus;
    }

    /** Returns a copy with only the focusability flag changed. */
    public NavigationInfo withFocusable(boolean focusable) {
        return copyBuilder().focusable(focusable).build();
    }

    /** Returns a copy with only the navigation order changed. */
    public NavigationInfo withOrder(int order) {
        return copyBuilder().order(order).build();
    }

    private Builder copyBuilder() {
        return builder(this.role)
                .id(this.id)
                .label(this.label)
                .actions(this.actions.toArray(new NavigationAction[0]))
                .group(this.group)
                .order(this.order)
                .primaryAxis(this.primaryAxis)
                .focusable(this.focusable)
                .wrapHorizontal(this.wrapHorizontal)
                .wrapVertical(this.wrapVertical)
                .trapFocus(this.trapFocus);
    }

    public static final class Builder {

        @Nullable private String id;
        private final NavigationRole role;
        private Supplier<String> label = () -> "";
        private final EnumSet<NavigationAction> actions = EnumSet.noneOf(NavigationAction.class);
        @Nullable private String group;
        private int order;
        private NavigationAxis primaryAxis = NavigationAxis.NONE;
        private boolean focusable;
        private boolean wrapHorizontal;
        private boolean wrapVertical;
        private boolean trapFocus;

        private Builder(NavigationRole role) {
            this.role = role == null ? NavigationRole.NONE : role;
            this.focusable = this.role != NavigationRole.NONE && this.role != NavigationRole.ROOT
                    && this.role != NavigationRole.PANEL && this.role != NavigationRole.GROUP
                    && this.role != NavigationRole.LIST && this.role != NavigationRole.TAB_LIST
                    && this.role != NavigationRole.MENU && this.role != NavigationRole.INVENTORY
                    && this.role != NavigationRole.SCROLL_VIEW;
        }

        public Builder id(@Nullable String id) {
            this.id = id;
            return this;
        }

        public Builder label(Supplier<String> label) {
            this.label = label == null ? () -> "" : label;
            return this;
        }

        public Builder actions(NavigationAction... actions) {
            if (actions != null) Collections.addAll(this.actions, actions);
            return this;
        }

        public Builder group(@Nullable String group) {
            this.group = group;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder primaryAxis(NavigationAxis primaryAxis) {
            this.primaryAxis = primaryAxis == null ? NavigationAxis.NONE : primaryAxis;
            return this;
        }

        public Builder focusable(boolean focusable) {
            this.focusable = focusable;
            return this;
        }

        public Builder wrap(boolean wrap) {
            this.wrapHorizontal = wrap;
            this.wrapVertical = wrap;
            return this;
        }

        public Builder wrapHorizontal(boolean wrap) {
            this.wrapHorizontal = wrap;
            return this;
        }

        public Builder wrapVertical(boolean wrap) {
            this.wrapVertical = wrap;
            return this;
        }

        public Builder trapFocus(boolean trapFocus) {
            this.trapFocus = trapFocus;
            return this;
        }

        public NavigationInfo build() {
            return new NavigationInfo(this);
        }
    }
}
