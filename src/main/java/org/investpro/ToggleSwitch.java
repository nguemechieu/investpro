package org.investpro;

import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.scene.control.Labeled;
import javafx.scene.control.Skin;

import java.util.Objects;

/**
 * A {@code ToggleSwitch} allows the user to toggle between two states, an "off"
 * state (where the thumb is to the left and the bar a neutral, gray color)
 * and an "on" state (where the thumb is to the right and the bar is a blue
 * color). A text label is displayed to the left (by default) of the ToggleSwitch
 * that displays the text name of the state that the switch is currently in, like so:
 *
 * <pre>
 *     Off   [[ ]------]
 *
 *     On    [------[ ]]
 *     </pre>
 * <p>
 * When the switch is toggled, the thumb slides to the left or right.
 */
public class ToggleSwitch extends Labeled {
    /**
     * Indicates whether this ToggleSwitch is selected.
     */
    private final ReadOnlyBooleanWrapper selected;
    final BooleanProperty turnOnTextLonger;

    private static final String DEFAULT_STYLE_CLASS = "toggle-switch";
    private static final PseudoClass PSEUDO_CLASS_SELECTED = PseudoClass.getPseudoClass("selected");

    /**
     * Creates a toggle switch with the default Strings of
     * "On" when the toggle switch is on and "Off" when it
     * is off.
     */
    public ToggleSwitch(@NamedArg("initialValue") boolean initialValue) {
        this(initialValue, "On", "Off");
    }

    /**
     * Creates a toggle switch that displays the given {@code textOn}
     * label when the switch is on, and {@code textOff} when the switch
     * is off.
     * <p>
     * Developer Note: It would be better to name the arguments "onText" and "offText",
     * but then we could not use the FXML @DefaultArg annotation, because an
     * argument cannot start with "on" because the FXMLLoader thinks it is
     * an event handler (see: {@code EVENT_HANDLER_PREFIX} definition in
     * FXMLLoader.java)
     *
     * @param initialValue the initial value of the switch
     * @param textOn the text to display when the switch is on
     * @param textOff the text to display when the switch is off
     */
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
        // we must explicitly call pseudoClassStateChanged so that the initial color of the thumb area is correct
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

    private final ReadOnlyStringWrapper turnOnText;

    public final ReadOnlyStringProperty turnOnTextProperty() {
        return turnOnText.getReadOnlyProperty();
    }

    public final String getTurnOnText() {
        return turnOnText.get();
    }

    private final ReadOnlyStringWrapper turnOffText;

    public final ReadOnlyStringProperty turnOffTextProperty() {
        return turnOffText.getReadOnlyProperty();
    }

    public final String getTurnOffText() {
        return turnOffText.get();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ToggleSwitchSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return Objects.requireNonNull(ToggleSwitch.class.getResource("/css/toggleswitch.css")).toExternalForm();
    }
}
