package org.investpro;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
class Test {
    private static final Logger logger = LoggerFactory.getLogger(Test.class);

    @BeforeEach
    void setUp() {


    }

    @AfterEach
    void tearDown() {
    }

 @org.junit.jupiter.api.Test
    void test() {
        logger.info("test");
        double a = 1.0;
        double b = 2.0;
        double c = 3.0;
        double d = 4.0;
        double e = 5.0;
        double sum = a + b + c + d + e;
        logger.info(String.valueOf(sum));

    }


}