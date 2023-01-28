/*
 * Copyright (c) 2016 by Gerrit Grunwald
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.investpro.investpro;

import javafx.scene.text.Font;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


public class Fonts {
    private static final String ROBOTO_LIGHT_NAME;
    private static final String ROBOTO_MEDIUM_NAME;

    private static String robotoLightName;
    private static String robotoMediumName;

    static {
        try {
            robotoLightName  = Font.loadFont(Fonts.class.getResourceAsStream("/eu/hansolo/fx/regulators/Roboto-Light.ttf"), 10).getName();
            robotoMediumName = Font.loadFont(Fonts.class.getResourceAsStream("/eu/hansolo/fx/regulators/Roboto-Medium.ttf"), 10).getName();
        } catch (Exception exception) {


            Log.error(String.valueOf(exception));
        }
        ROBOTO_LIGHT_NAME  = robotoLightName;
        ROBOTO_MEDIUM_NAME = robotoMediumName;
    }


    // ******************** Methods *******************************************
    @Contract("_ -> new")
    public static @NotNull Font robotoLight(final double SIZE) { return new Font(ROBOTO_LIGHT_NAME, SIZE); }
    @Contract("_ -> new")
    public static @NotNull Font robotoMedium(final double SIZE) { return new Font(ROBOTO_MEDIUM_NAME, SIZE); }
}