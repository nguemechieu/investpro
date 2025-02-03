package org.investpro;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.css.PseudoClass;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import javax.tools.Tool;
import java.util.Objects;

/**
 * 📌 **Toolbar Button for Candlestick Chart Toolbar**
 * - Handles zooming, printing, and settings
 * - Can dynamically update UI when selected
 */
@Getter
@Setter
public class ToolbarButton extends Button {
    protected final int duration;
    private final PseudoClass activeClass = PseudoClass.getPseudoClass("active");
    private final BooleanProperty active = new BooleanPropertyBase(false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(activeClass, get());
        }

        @Override
        public Object getBean() {
            return ToolbarButton.this;
        }

        @Override
        public @NotNull String getName() {
            return "active";
        }
    };
    Tool tool;
    private ImageView icon;

    /**
     * **Constructor for toolbar buttons (Zoom, Print, Settings)**
     *
     * @param tool      Enum defining the tool type
     *                  <p>
     *                  <p>
     *                  /**
     *                  **Base Constructor**
     * @param textLabel Label text (optional)
     * @param imgPath   Icon path (optional)
     * @param duration  Time interval (optional)
     */
    private ToolbarButton(String textLabel, Tool tool, String imgPath, int duration) {
        this.tool = tool;
        this.duration = duration;

        if (textLabel != null) {
            setText(textLabel);
        }


        if (imgPath != null) {
            icon = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream(imgPath))));
            setGraphic(icon);
        }
        setMinSize(5, 5);
        setMaxSize(20, 20);
        getStyleClass().add("candle-chart-toolbar-button");
    }

    /**
     * Activates or deactivates the button
     */
    public void setActive(boolean active) {
        this.active.set(active);
    }
}
