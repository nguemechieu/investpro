import org.investpro.exchange.coinbase.Coinbase;
import org.investpro.exchange.credentials.ExchangeCredentialResolver;
import org.investpro.exchange.providers.EnvironmentCredentialProvider;
public class CoinbaseConnectionProbe {
 public static void main(String[] args) {
  ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
  var credentials=new ExchangeCredentialResolver(new EnvironmentCredentialProvider()).resolve("coinbase");
  if (!credentials.hasApiKeySecret() && !credentials.hasCoinbaseAdvancedTradeCredentials()) {
   System.out.println("Coinbase key and secret are not configured; live verification skipped."); return;
  }
  try {
   var result=new Coinbase(credentials).checkAuthentication();
   System.out.println("Read-only Coinbase accounts authentication: success="+result.isSuccess()+", HTTP="+result.getHttpStatus());
   System.out.println(result.getMessage());
  } catch (Exception error) {
   System.out.println("Configured Coinbase credentials could not be loaded; key contents omitted.");
  }
 }
}
