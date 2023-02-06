package org.investpro.investpro;

import java.beans.Encoder;
import java.util.Base64;

public interface CodecFactory {

    Encoder getEncoder(String encodingName);

    Base64.Decoder getDecoder(String encodingName);

}
