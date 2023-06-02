package org.investpro;

public enum InpMoneyManagement {
    FIXED_SIZE(0),
    MARTIN_GALES(1),
    SIMPLE_GALES(2),
    Market_Volume_Risk(3), Risk_Percent_Per_Trade(4), MARTINGALE_OR_ANTI_MATINGALE(5), LOT_OPTIMIZE(6), POSITION_SIZE(7);

    InpMoneyManagement(int i) {
    }
}
