package org.investpro.ui.screens;

import javafx.scene.Parent;
import org.investpro.ui.OnboardingDesk;
import org.investpro.ui.navigation.Screen;
import org.investpro.ui.theme.MarketConfiguration;

import java.util.Objects;
import java.util.function.Consumer;

public class OnboardingScreen implements Screen {

    private final OnboardingDesk view;

    public OnboardingScreen(Consumer<MarketConfiguration> onReady) {
        this.view = new OnboardingDesk(Objects.requireNonNull(onReady, "onReady must not be null"));
    }

    @Override
    public Parent getView() {
        return view;
    }
}
