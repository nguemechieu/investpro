package org.investpro.investpro;

import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.scene.control.Labeled;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class ToggleSwitch extends Labeled {
    private static final String DEFAULT_STYLE_CLASS = "toggle-switch";
    private static final PseudoClass PSEUDO_CLASS_SELECTED = PseudoClass.getPseudoClass("selected");
    final BooleanProperty turnOnTextLonger;
    private final ReadOnlyBooleanWrapper selected;
    private final ReadOnlyStringWrapper turnOnText;
    private final ReadOnlyStringWrapper turnOffText;
    protected boolean on;

    public ToggleSwitch(@NamedArg("initialValue") boolean initialValue) {
        this(initialValue, "On", "Off");
    }

    public ToggleSwitch(@NamedArg("initialValue") boolean initialValue,
                        @NamedArg("textOn") String textOn,
                        @NamedArg("textOff") String textOff) {
        selected = new ReadOnlyBooleanWrapper(initialValue) {
            @Override
            protected void invalidated() {
                pseudoClassStateChanged(PSEUDO_CLASS_SELECTED, get());
            }

            @Override
            public Object getBean() {
                return ToggleSwitch.this;
            }

            @Override
            public String getName() {
                return "selected";
            }
        };

        turnOnText = new ReadOnlyStringWrapper(textOn);
        turnOffText = new ReadOnlyStringWrapper(textOff);
        turnOnTextLonger = new ReadOnlyBooleanWrapper();
        turnOnTextLonger.bind(turnOnText.length().greaterThan(turnOffText.length()));
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
        pseudoClassStateChanged(PSEUDO_CLASS_SELECTED, selected.get());
    }

    public final boolean isOn() {
        return selected.get();
    }

    public final ReadOnlyBooleanProperty selectedProperty() {
        return selected.getReadOnlyProperty();
    }

    protected final void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public final String getTurnOnText() {
        return turnOnText.get();
    }

    public final String getTurnOffText() {
        return turnOffText.get();
    }

    @Override
    public String getUserAgentStylesheet() {
        return Objects.requireNonNull(ToggleSwitch.class.getResource("/css/toggleswitch.css")).toExternalForm();
    }

}