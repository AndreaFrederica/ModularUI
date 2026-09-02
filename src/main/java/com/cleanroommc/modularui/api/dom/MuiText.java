package com.cleanroommc.modularui.api.dom;

import java.util.Objects;

public final class MuiText extends MuiNode {

    private String data;

    MuiText(MuiDocument document, NodeHandle handle, String data) {
        super(document, handle);
        this.data = Objects.requireNonNull(data, "data");
    }

    public String getData() {
        requireAlive();
        return this.data;
    }

    public void setData(String data) {
        requireAlive();
        getOwnerDocument().setTextData(this, Objects.requireNonNull(data, "data"));
    }

    void setDataDirect(String data) {
        this.data = data;
    }

    @Override
    public String getTextContent() {
        return getData();
    }

    @Override
    void appendTextContent(StringBuilder result) {
        result.append(this.data);
    }
}
