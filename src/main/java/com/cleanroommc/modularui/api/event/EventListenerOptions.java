package com.cleanroommc.modularui.api.event;

/** Immutable options for a DOM-style event listener. */
public final class EventListenerOptions {

    public static final EventListenerOptions DEFAULT = new EventListenerOptions(false, false, false);
    public static final EventListenerOptions CAPTURE = new EventListenerOptions(true, false, false);

    private final boolean capture;
    private final boolean once;
    private final boolean passive;

    private EventListenerOptions(boolean capture, boolean once, boolean passive) {
        this.capture = capture;
        this.once = once;
        this.passive = passive;
    }

    public boolean isCapture() {
        return this.capture;
    }

    public boolean isOnce() {
        return this.once;
    }

    public boolean isPassive() {
        return this.passive;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private boolean capture;
        private boolean once;
        private boolean passive;

        public Builder capture(boolean capture) {
            this.capture = capture;
            return this;
        }

        public Builder once(boolean once) {
            this.once = once;
            return this;
        }

        public Builder passive(boolean passive) {
            this.passive = passive;
            return this;
        }

        public EventListenerOptions build() {
            if (!this.capture && !this.once && !this.passive) return DEFAULT;
            if (this.capture && !this.once && !this.passive) return CAPTURE;
            return new EventListenerOptions(this.capture, this.once, this.passive);
        }
    }
}
